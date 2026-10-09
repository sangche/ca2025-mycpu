// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv

import chisel3._
import riscv.core.CPU
import riscv.peripheral._

import my.Foo

class TestTopModule(exeFilename: String) extends Module {
  val io = IO(new Bundle {
    val mem_debug_read_address  = Input(UInt(Parameters.AddrWidth))
    val regs_debug_read_address = Input(UInt(Parameters.PhysicalRegisterAddrWidth))
    val regs_debug_read_data    = Output(UInt(Parameters.DataWidth))
    val mem_debug_read_data     = Output(UInt(Parameters.DataWidth))

    // FOO test
    val foo_valid_count_test = Output(UInt(32.W))
  })

  val mem             = Module(new Memory(8192))
  val instruction_rom = Module(new InstructionROM(exeFilename))
  val rom_loader      = Module(new ROMLoader(1024))

  rom_loader.io.rom_data     := instruction_rom.io.data
  rom_loader.io.load_address := Parameters.EntryAddress
  instruction_rom.io.address := rom_loader.io.rom_address

  val CPU_clkdiv = RegInit(UInt(2.W), 0.U)
  val CPU_tick   = Wire(Bool())
  val CPU_next   = Wire(UInt(2.W))
  CPU_next   := Mux(CPU_clkdiv === 3.U, 0.U, CPU_clkdiv + 1.U)
  CPU_tick   := CPU_clkdiv === 0.U
  CPU_clkdiv := CPU_next

  withClock(CPU_tick.asClock) {
    val cpu = Module(new CPU)
    val foo = Module(new Foo)

    // Count foo_valid assertions on CPU clock edges.
    val fooValidCount = RegInit(0.U(32.W))
    when(cpu.io.foo_valid) {
      fooValidCount := fooValidCount + 1.U
    }
    io.foo_valid_count_test := fooValidCount

    // CPU register -> Foo input
    foo.io.a     := cpu.io.foo_a
    foo.io.valid := cpu.io.foo_valid

    // Foo output -> CPU
    cpu.io.foo_b    := foo.io.b
    cpu.io.foo_done := foo.io.done

    // CPU interface
    cpu.io.debug_read_address  := 0.U
    cpu.io.instruction_valid   := rom_loader.io.load_finished
    cpu.io.instruction         := mem.io.instruction
    mem.io.instruction_address := cpu.io.instruction_address

    when(!rom_loader.io.load_finished) {
      rom_loader.io.bundle <> mem.io.bundle
      cpu.io.memory_bundle.read_data := 0.U
    }.otherwise {
      rom_loader.io.bundle.read_data := 0.U
      cpu.io.memory_bundle <> mem.io.bundle
    }

    cpu.io.debug_read_address := io.regs_debug_read_address
    io.regs_debug_read_data   := cpu.io.debug_read_data
  }

  mem.io.debug_read_address := io.mem_debug_read_address
  io.mem_debug_read_data    := mem.io.debug_read_data
}
