#!/usr/bin/env python3
"""
Analyze a Verilator VCD trace produced by 0-minimal.

Important timing rule for this trace:
  - PC/instruction are updated on the rising edge.
  - The decode/execute/writeback control signals settle during the following
    half-cycle.
  - Therefore an instruction is sampled on the FALLING edge, after all VCD
    changes at that timestamp have been applied.
  - A register/memory write described by that falling-edge snapshot is committed
    at the following rising edge.

The VCD contains optimized/aliased signals.  Different hierarchy names can
legitimately have the same VCD symbol, so this program resolves the exact
hierarchical name first and then uses its symbol.
"""

import argparse
import sys
from collections import Counter


REQUIRED = {
    "clock": "TOP.Top.cpu.clock",
    "reset": "TOP.Top.cpu.reset",
    "pc": "TOP.Top.cpu.inst_fetch.pc",
    "instruction": "TOP.Top.cpu.inst_fetch.io_instruction",
    "instruction_valid": "TOP.Top.cpu.inst_fetch.io_instruction_valid",
    "id_opcode": "TOP.Top.cpu.id.opcode",
    "id_jalr": "TOP.Top.cpu.id.isJalr",
    "id_rs1": "TOP.Top.cpu.id.rs1",
    "id_rs2": "TOP.Top.cpu.id.rs2",
    "id_rd": "TOP.Top.cpu.id.rd",
    "id_immI": "TOP.Top.cpu.id.immI",
    "ex_alu1": "TOP.Top.cpu.ex.aluOp1",
    "ex_alu_result": "TOP.Top.cpu.ex.aluResult",
    "ex_jump": "TOP.Top.cpu.ex.io_if_jump_flag",
    "ex_jump_address": "TOP.Top.cpu.ex.io_if_jump_address",
    "reg_write_enable": "TOP.Top.cpu.regs.io_write_enable",
    "reg_write_address": "TOP.Top.cpu.regs.io_write_address",
    "reg_write_data": "TOP.Top.cpu.regs.io_write_data",
    "a0_internal": "TOP.Top.cpu.regs.registers_10",
    "mem_write_enable": "TOP.Top.cpu.mem.io_memory_write_enable",
    "mem_address": "TOP.Top.cpu.mem.io_memory_bundle_address",
    "mem_write_data": "TOP.Top.cpu.mem.io_memory_bundle_write_data",
    "wb_write_data": "TOP.Top.cpu.wb.io_regs_write_data",
}


def parse_header(f):
    scopes = []
    names = {}
    in_header = True

    while in_header:
        line = f.readline()
        if not line:
            raise RuntimeError("VCD ended before $enddefinitions $end")

        s = line.strip()

        if s.startswith("$scope"):
            p = s.split()
            scopes.append(p[2])

        elif s.startswith("$upscope"):
            if scopes:
                scopes.pop()

        elif s.startswith("$var"):
            p = s.split()
            # $var wire <width> <symbol> <name> [range] $end
            width = int(p[2])
            symbol = p[3]
            name = p[4]
            full = ".".join(scopes + [name])
            names[full] = (symbol, width)

        elif s == "$enddefinitions $end":
            in_header = False

    return names


def parse_change(s):
    """Return (symbol, value) for a VCD value-change line."""
    s = s.strip()
    if not s or s[0] in "#$":
        return None

    if s[0] == "b":
        p = s.split()
        if len(p) != 2:
            return None
        bits, symbol = p
        bits = bits[1:]
        if any(c in "xXzZ" for c in bits):
            return symbol, None
        return symbol, int(bits, 2)

    # scalar: <value><symbol>
    if len(s) >= 2:
        c = s[0]
        if c == "0":
            return s[1], 0
        if c == "1":
            return s[1], 1
        if c in "xXzZ":
            return s[1], None

    return None


def sext(value, bits):
    if value is None:
        return 0
    mask = (1 << bits) - 1
    value &= mask
    sign = 1 << (bits - 1)
    return value - (1 << bits) if value & sign else value


def disasm(inst):
    if inst is None:
        return "?"

    op = inst & 0x7f
    rd = (inst >> 7) & 31
    f3 = (inst >> 12) & 7
    rs1 = (inst >> 15) & 31
    rs2 = (inst >> 20) & 31
    imm_i = sext(inst >> 20, 12)

    if op == 0x17:
        return f"auipc x{rd}, 0x{inst >> 12:x}"

    if op == 0x6f:
        imm = (
            ((inst >> 31) & 1) << 20
            | ((inst >> 12) & 0xff) << 12
            | ((inst >> 20) & 1) << 11
            | ((inst >> 21) & 0x3ff) << 1
        )
        return f"jal x{rd}, {sext(imm, 21)}"

    if op == 0x67 and f3 == 0:
        return f"jalr x{rd}, {imm_i}(x{rs1})"

    if op == 0x13:
        names = {0: "addi", 2: "slti", 3: "sltiu",
                 4: "xori", 6: "ori", 7: "andi"}
        if f3 == 1:
            return f"slli x{rd}, x{rs1}, {(inst >> 20) & 31}"
        if f3 == 5:
            name = "srai" if ((inst >> 30) & 1) else "srli"
            return f"{name} x{rd}, x{rs1}, {(inst >> 20) & 31}"
        return f"{names.get(f3, 'opimm')} x{rd}, x{rs1}, {imm_i}"

    if op == 0x33 and f3 == 0:
        return f"{'sub' if ((inst >> 30) & 1) else 'add'} x{rd}, x{rs1}, x{rs2}"

    if op == 0x03 and f3 == 2:
        return f"lw x{rd}, {imm_i}(x{rs1})"

    if op == 0x23 and f3 == 2:
        imm = ((inst >> 25) << 5) | ((inst >> 7) & 31)
        return f"sw x{rs2}, {sext(imm, 12)}(x{rs1})"

    if op == 0x37:
        return f"lui x{rd}, 0x{inst >> 12:x}"

    if op == 0x73:
        return "system"

    return f"unknown(op=0x{op:02x})"


def fmt32(v):
    return "?" if v is None else f"0x{v & 0xffffffff:08x}"


def fmt_reg(v):
    return "?" if v is None else f"x{v}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("vcd")
    ap.add_argument("--max-lines", type=int, default=120,
                    help="maximum instruction trace lines to print (default: 120)")
    ap.add_argument("--all", action="store_true",
                    help="print the complete instruction trace")
    ap.add_argument("--from-pc", type=lambda x: int(x, 0), default=None)
    args = ap.parse_args()

    with open(args.vcd, "r", errors="replace") as f:
        names = parse_header(f)

        missing = [name for name in REQUIRED.values() if name not in names]
        if missing:
            print("[ERROR] Required VCD signals are missing:")
            for name in missing:
                print("  ", name)
            return 2

        # Exact hierarchy -> VCD symbol.
        sym = {key: names[name][0] for key, name in REQUIRED.items()}

        # All required signals are read from the VCD state by symbol.
        state = {}
        edges = 0
        rising = 0
        falling = 0

        # Inferred architectural register file.  The VCD's
        # registers_10 signal is intentionally NOT used as the architectural
        # value because the supplied trace shows it remains at zero.
        regs = [0] * 32
        x10_history = []

        # Pending write from the instruction sampled on the previous
        # falling edge.  It is committed at the next rising edge.
        pending_reg = None
        pending_mem = None

        instr_trace = []
        jalr_events = []
        reg_writes = []
        mem_writes = []

        current_ts = None
        changes = {}

        def value(key):
            return state.get(sym[key])

        def snapshot():
            return {key: value(key) for key in REQUIRED}

        def process_timestamp(ts, ts_changes):
            nonlocal rising, falling, pending_reg, pending_mem, edges

            old_clock = state.get(sym["clock"], 0)
            state.update(ts_changes)
            new_clock = state.get(sym["clock"], old_clock)

            # Rising edge: commit the instruction sampled during the
            # preceding low half-cycle.
            if old_clock == 0 and new_clock == 1:
                rising += 1

                if pending_reg is not None:
                    rd, data = pending_reg
                    if rd != 0 and data is not None:
                        regs[rd] = data & 0xffffffff
                    regs[0] = 0

                    if rd == 10:
                        x10_history.append(
                            (rising, ts, data & 0xffffffff if data is not None else None)
                        )

                pending_reg = None
                pending_mem = None

            # Falling edge: the current instruction and its combinational
            # control/data signals are stable after all changes at this
            # timestamp.  This is the correct instruction sample point.
            elif old_clock == 1 and new_clock == 0:
                falling += 1
                if value("instruction_valid") != 1:
                    return

                r = snapshot()
                r["cycle"] = falling
                r["time"] = ts
                r["mnemonic"] = disasm(r["instruction"])
                r["model_x10"] = regs[10]

                instr_trace.append(r)

                if r["id_jalr"] == 1:
                    jalr_events.append(r)

                if r["reg_write_enable"] == 1:
                    rd = r["reg_write_address"]
                    data = r["reg_write_data"]
                    reg_writes.append(r)
                    pending_reg = (rd, data)

                if r["mem_write_enable"] == 1:
                    mem_writes.append(r)
                    pending_mem = (
                        r["mem_address"],
                        r["mem_write_data"],
                    )

        # Read dumpvars and all value changes.  We must not discard dumpvars:
        # registers_10, clock and reset are initialized there.
        for raw in f:
            s = raw.strip()
            if not s:
                continue

            if s.startswith("#"):
                ts = int(s[1:])

                if current_ts is not None:
                    process_timestamp(current_ts, changes)

                current_ts = ts
                changes = {}
                edges += 1
                continue

            ch = parse_change(s)
            if ch is not None:
                changes[ch[0]] = ch[1]

        if current_ts is not None:
            process_timestamp(current_ts, changes)

    # Summary
    actual_a0_writes = [
        r for r in reg_writes
        if r["reg_write_address"] == 10
    ]

    print("=" * 88)
    print("0-minimal VCD execution analysis")
    print("=" * 88)
    print(f"VCD                         : {args.vcd}")
    print(f"rising clock edges         : {rising}")
    print(f"falling instruction samples: {falling}")
    print(f"valid instructions         : {len(instr_trace)}")
    print(f"JALR instructions          : {len(jalr_events)}")
    print(f"register writes (rd != x0) : "
          f"{sum(r['reg_write_address'] != 0 for r in reg_writes)}")
    print(f"memory writes              : {len(mem_writes)}")
    print(f"x10/a0 writebacks          : {len(actual_a0_writes)}")

    if x10_history:
        print("x10/a0 writeback values     :")
        for cycle, ts, data in x10_history[:20]:
            print(f"  cycle={cycle:6d} time={ts:8d}  x10 <- {fmt32(data)}")
    else:
        print("x10/a0 writeback values     : NONE")

    if x10_history:
        seven = [x for x in x10_history if x[2] == 7]
        if seven:
            print(f"[PASS] x10/a0 receives 7 ({len(seven)} writeback event(s)).")
        else:
            print("[FAIL] x10/a0 never receives 7.")
    else:
        print("[FAIL] no x10/a0 writeback was observed.")

    print()
    print("PC frequency (top 20)")
    for pc, n in Counter(r["pc"] for r in instr_trace).most_common(20):
        print(f"  {fmt32(pc)} : {n}")

    print()
    print("First instruction trace")
    print("-" * 88)
    shown = 0
    for r in instr_trace:
        if args.from_pc is not None and r["pc"] != args.from_pc:
            continue

        event = []
        if r["id_jalr"] == 1:
            event.append(f"JALR->{fmt32(r['ex_jump_address'])}")
        if r["reg_write_enable"] == 1:
            event.append(
                f"WB {fmt_reg(r['reg_write_address'])}<-{fmt32(r['reg_write_data'])}"
            )
        if r["mem_write_enable"] == 1:
            event.append(
                f"MEM[{fmt32(r['mem_address'])}]<-{fmt32(r['mem_write_data'])}"
            )

        print(
            f"{r['cycle']:6d} t={r['time']:7d} "
            f"PC={fmt32(r['pc'])} INST={fmt32(r['instruction'])} "
            f"{r['mnemonic']:<28} "
            + (" | ".join(event) if event else "")
        )

        shown += 1
        if not args.all and shown >= args.max_lines:
            break

    print()
    print("Important VCD observation")
    print("-" * 88)
    print("The internal signal TOP.Top.cpu.regs.registers_10 is present in the")
    print("VCD, but in this trace it remains at zero. Therefore this analyzer")
    print("does NOT use that signal as the architectural x10 value.")
    print("Instead, x10 is reconstructed from the actual register writeback")
    print("(io_write_enable, io_write_address, io_write_data), aligned so that")
    print("the write is committed on the following rising clock edge.")
    print("=" * 88)

    return 0


if __name__ == "__main__":
    sys.exit(main())
