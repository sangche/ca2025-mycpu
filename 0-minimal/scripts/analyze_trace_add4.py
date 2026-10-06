#!/usr/bin/env python3

"""
VCD Trace Analyzer for 0-minimal RISC-V CPU

Analyzes a VCD trace to verify execution of add4.asmbin.

Expected program:

    _start @ 0x1000
        |
        +--> main @ 0x1044
                |
                +--> add4 @ 0x1014
                        |
                        +--> a0 = 7
                |
                +--> return 7
        |
        +--> halt loop @ 0x100c
"""

import argparse
import sys
from collections import defaultdict
from typing import Dict, DefaultDict


# ----------------------------------------------------------------------
# add4.asmbin layout
# ----------------------------------------------------------------------

ENTRY_POINT = 0x1000
HALT_ADDRESS = 0x100C
ADD4_ADDRESS = 0x1014
MAIN_ADDRESS = 0x1044

A0_REGISTER_INDEX = 10
EXPECTED_A0_VALUE = 7


# ----------------------------------------------------------------------
# VCD signal names
# ----------------------------------------------------------------------

PC_SIGNAL = 'io_instruction_address'
REG_WRITE_ADDR_SIGNAL = 'regs_io_write_address'
REG_WRITE_DATA_SIGNAL = 'regs_io_write_data'
MEM_WRITE_ENABLE_SIGNAL = 'io_memory_bundle_write_enable'


class VCDAnalyzer:
    """Parses and analyzes VCD files for add4.asmbin execution."""

    def __init__(self, vcd_file: str):
        self.vcd_file = vcd_file

        self.signals: Dict[str, str] = {}

        self.target_signals = {
            PC_SIGNAL,
            REG_WRITE_ADDR_SIGNAL,
            REG_WRITE_DATA_SIGNAL,
            MEM_WRITE_ENABLE_SIGNAL,
        }

        self.signal_symbols: Dict[str, str] = {}

    @staticmethod
    def binary_to_int(binary_str: str) -> int:
        """Convert a binary VCD value to an integer."""
        if 'x' in binary_str or 'z' in binary_str:
            return 0
        return int(binary_str, 2)

    def parse_and_analyze(self) -> DefaultDict[str, int or bool]:
        """Parse the VCD and collect execution statistics."""

        stats: DefaultDict[str, int or bool] = defaultdict(int)

        stats['a0_value_7'] = False
        stats['entry_seen'] = False
        stats['main_seen'] = False
        stats['add4_seen'] = False
        stats['halt_seen'] = False

        try:
            with open(self.vcd_file, 'r') as f:

                in_definitions = True

                for line in f:
                    line = line.strip()

                    if not line:
                        continue

                    # --------------------------------------------------
                    # VCD signal definitions
                    # --------------------------------------------------
                    if in_definitions:

                        if line.startswith('$var'):
                            parts = line.split()

                            if len(parts) >= 5:
                                signal_name = parts[4]
                                symbol = parts[3]

                                if signal_name in self.target_signals:
                                    self.signals[symbol] = signal_name
                                    self.signal_symbols[signal_name] = symbol

                        elif line.startswith('$enddefinitions'):
                            in_definitions = False

                        continue

                    # --------------------------------------------------
                    # Skip timestamps
                    # --------------------------------------------------
                    if line.startswith('#'):
                        continue

                    # --------------------------------------------------
                    # Multi-bit value changes
                    # --------------------------------------------------
                    if line.startswith('b'):

                        space_idx = line.find(' ')

                        if space_idx <= 0:
                            continue

                        value = line[1:space_idx]
                        symbol = line[space_idx + 1:]

                        signal = self.signals.get(symbol)

                        if signal == PC_SIGNAL:

                            pc = self.binary_to_int(value)

                            if pc != 0:
                                stats['pc_samples'] += 1

                                if pc == ENTRY_POINT:
                                    stats['entry_seen'] = True

                                if pc == MAIN_ADDRESS:
                                    stats['main_seen'] = True

                                if pc == ADD4_ADDRESS:
                                    stats['add4_seen'] = True

                                if pc == HALT_ADDRESS:
                                    stats['halt_seen'] = True

                                if pc > stats['max_pc']:
                                    stats['max_pc'] = pc

                        elif signal == REG_WRITE_ADDR_SIGNAL:

                            reg = self.binary_to_int(value)

                            stats['register_writes'] += 1

                            if reg == A0_REGISTER_INDEX:
                                stats['a0_writes'] += 1
                                stats['last_a0_write'] = 1

                        elif signal == REG_WRITE_DATA_SIGNAL:

                            data = self.binary_to_int(value)

                            stats['last_register_write_data'] = data

                            if data == EXPECTED_A0_VALUE:
                                stats['a0_value_7'] = True

                    # --------------------------------------------------
                    # Single-bit value changes
                    # --------------------------------------------------
                    elif len(line) >= 2 and line[0] in '01xz':

                        value = line[0]
                        symbol = line[1:]

                        signal = self.signals.get(symbol)

                        if (
                            signal == MEM_WRITE_ENABLE_SIGNAL
                            and value == '1'
                        ):
                            stats['memory_writes'] += 1

        except FileNotFoundError:
            print(f"Error: VCD file not found at '{self.vcd_file}'")
            sys.exit(1)

        except Exception as e:
            print(f"Error parsing VCD file: {e}")
            sys.exit(1)

        return stats

    def print_report(
        self,
        stats: DefaultDict[str, int or bool]
    ) -> bool:
        """Print the add4 verification report."""

        execution_ok = (
            stats['entry_seen']
            and stats['main_seen']
            and stats['add4_seen']
            and stats['a0_value_7']
            and stats['halt_seen']
        )

        status = "[PASS]" if execution_ok else "[FAIL]"

        print("=" * 70)
        print(" VCD Trace Analysis Report - add4.asmbin")
        print("=" * 70)

        print(f"\nOverall Status: {status}\n")

        print("Execution:")
        print(
            f"  - Entry point 0x{ENTRY_POINT:08x}: "
            f"{'YES' if stats['entry_seen'] else 'NO'}"
        )

        print(
            f"  - main @ 0x{MAIN_ADDRESS:08x}: "
            f"{'YES' if stats['main_seen'] else 'NO'}"
        )

        print(
            f"  - add4 @ 0x{ADD4_ADDRESS:08x}: "
            f"{'YES' if stats['add4_seen'] else 'NO'}"
        )

        print(
            f"  - halt loop @ 0x{HALT_ADDRESS:08x}: "
            f"{'YES' if stats['halt_seen'] else 'NO'}"
        )

        print("\nRegister:")
        print(
            f"  - a0 (x{A0_REGISTER_INDEX}) == 7: "
            f"{'YES' if stats['a0_value_7'] else 'NO'}"
        )

        print(
            f"  - Writes to a0: "
            f"{stats['a0_writes']}"
        )

        print("\nMemory:")
        print(
            f"  - Memory writes: "
            f"{stats['memory_writes']}"
        )

        print("\nDetailed Statistics:")
        print(
            f"  - PC samples: "
            f"{stats['pc_samples']}"
        )

        print(
            f"  - Max PC address: "
            f"0x{stats['max_pc']:08x}"
        )

        print(
            f"  - Register writes: "
            f"{stats['register_writes']}"
        )

        print("\nExpected Program Layout:")
        print(f"  - Entry Point: 0x{ENTRY_POINT:08x}")
        print(f"  - add4:        0x{ADD4_ADDRESS:08x}")
        print(f"  - main:        0x{MAIN_ADDRESS:08x}")
        print(f"  - Halt Loop:   0x{HALT_ADDRESS:08x}")
        print(f"  - Expected a0: {EXPECTED_A0_VALUE}")

        print("\nInterpretation:")

        if execution_ok:
            print("  - [OK] _start executed.")
            print("  - [OK] main executed.")
            print("  - [OK] add4 executed.")
            print("  - [OK] add4 returned 7 in a0.")
            print("  - [OK] main returned 7.")
            print("  - [OK] CPU reached the halt loop.")
        else:
            print("  - [FAIL] add4 execution did not complete as expected.")

        print("\n" + "=" * 70)

        return execution_ok


def main() -> None:

    parser = argparse.ArgumentParser(
        description='VCD analyzer for add4.asmbin.'
    )

    parser.add_argument(
        'vcd_file',
        help='Path to the VCD trace file.'
    )

    args = parser.parse_args()

    print(f"Analyzing '{args.vcd_file}'...")

    analyzer = VCDAnalyzer(args.vcd_file)

    stats = analyzer.parse_and_analyze()

    print(
        f"Parsed {len(analyzer.signals)} relevant signals."
    )

    success = analyzer.print_report(stats)

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()