
package riscv.core

import chisel3._
import riscv.CPUBundle

// Minimal CPU with Foo accelerator support.
// Normal instructions: AUIPC, ADDI, LW, SW, JALR
// Custom instruction: FOO (opcode 0x0B)
class CPU extends Module {
  val io = IO(new CPUBundle)

  val regs       = Module(new RegisterFile)
  val inst_fetch = Module(new InstructionFetch)
  val id         = Module(new InstructionDecode)
  val ex         = Module(new Execute)
  val mem        = Module(new MemoryAccess)
  val wb         = Module(new WriteBack)

  // ------------------------------------------------------------
  // Foo accelerator control
  // ------------------------------------------------------------
  val fooBusy = RegInit(false.B)
  val fooRd   = RegInit(0.U(5.W))

  val isFoo = inst_fetch.io.instruction(6, 0) === InstructionTypes.Foo

  // A one-cycle pulse: start Foo only once for this instruction.
  val fooStart =
    io.instruction_valid && !fooBusy && isFoo

  // Foo result is committed when done is asserted while busy.
  val fooFinish = fooBusy && io.foo_done

  when(fooStart) {
    fooBusy := true.B
    fooRd   := inst_fetch.io.instruction(11, 7)
  }.elsewhen(fooFinish) {
    fooBusy := false.B
  }

  // CPU -> Foo
  io.foo_a     := regs.io.read_data1
  io.foo_valid := fooStart

  // ------------------------------------------------------------
  // Instruction Fetch
  // ------------------------------------------------------------
  // During Foo execution, hold the PC and inject NOPs.
  inst_fetch.io.jump_address_id       := ex.io.if_jump_address
  inst_fetch.io.jump_flag_id          := ex.io.if_jump_flag
  inst_fetch.io.instruction_valid     := io.instruction_valid && !fooBusy
  inst_fetch.io.instruction_read_data := io.instruction
  io.instruction_address              := inst_fetch.io.instruction_address

  // ------------------------------------------------------------
  // Instruction Decode
  // ------------------------------------------------------------
  id.io.instruction := inst_fetch.io.instruction

  // ------------------------------------------------------------
  // Register File
  // ------------------------------------------------------------
  // Suppress normal writeback while Foo is busy.
  // When Foo finishes, write its result to the saved rd.
  regs.io.write_enable :=
    (id.io.reg_write_enable && !fooBusy) || fooFinish

  regs.io.write_address :=
    Mux(fooFinish, fooRd, id.io.reg_write_address)

  regs.io.write_data :=
    Mux(fooFinish, io.foo_b, wb.io.regs_write_data)

  regs.io.read_address1      := id.io.regs_reg1_read_address
  regs.io.read_address2      := id.io.regs_reg2_read_address
  regs.io.debug_read_address := io.debug_read_address
  io.debug_read_data         := regs.io.debug_read_data

  // ------------------------------------------------------------
  // Execute
  // ------------------------------------------------------------
  ex.io.aluop1_source       := id.io.ex_aluop1_source
  ex.io.immediate           := id.io.ex_immediate
  ex.io.instruction         := inst_fetch.io.instruction
  ex.io.instruction_address := inst_fetch.io.instruction_address
  ex.io.reg1_data           := regs.io.read_data1

  // ------------------------------------------------------------
  // Memory Access
  // ------------------------------------------------------------
  mem.io.alu_result          := ex.io.mem_alu_result
  mem.io.reg2_data           := regs.io.read_data2
  mem.io.memory_read_enable  := id.io.memory_read_enable && !fooBusy
  mem.io.memory_write_enable := id.io.memory_write_enable && !fooBusy
  io.memory_bundle <> mem.io.memory_bundle

  // ------------------------------------------------------------
  // Normal Write Back
  // ------------------------------------------------------------
  wb.io.instruction_address := inst_fetch.io.instruction_address
  wb.io.alu_result          := ex.io.mem_alu_result
  wb.io.memory_read_data    := mem.io.wb_memory_read_data
  wb.io.regs_write_source   := id.io.wb_reg_write_source
}