// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class JITTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("Minimal CPU - JIT Test")

  it should "correctly execute jit.asmbin and set a0 to 42" in {
    test(new TestTopModule("jit.asmbin")) { c =>
      // Run for enough cycles to complete JIT code execution
      // Program copies instructions to buffer, executes them, and sets a0=42
      for (i <- 1 to 50) {
        c.clock.step(1000)
        c.io.mem_debug_read_address.poke((i * 4).U)
      }

      // Verify a0 register (x10) = 42 via debug interface
      c.io.regs_debug_read_address.poke(10.U)
      c.clock.step()
      c.io.regs_debug_read_data.expect(42.U)
    }
  }
}

class Add4Test extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("Minimal CPU - Add4 Test")

  it should "correctly execute add4.asmbin and set a0 to 7" in {
    test(new TestTopModule("add4.asmbin")) { c =>
      // Run for enough cycles to complete JIT code execution
      // Program copies instructions to buffer, executes them, and sets a0=42
      for (i <- 1 to 50) {
        c.clock.step(1000)
        c.io.mem_debug_read_address.poke((i * 4).U)
      }

      // Verify a0 register (x10) = 42 via debug interface
      c.io.regs_debug_read_address.poke(10.U)
      c.clock.step()
      c.io.regs_debug_read_data.expect(7.U)
    }
  }
}

class FooTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("Minimal CPU - foo Test")

  it should "foo foo..." in {
    test(new my.Foo) { c =>
      c.io.a.poke(5.U)
      for (i <- 0 to 6) {
        println(s"Cycle $i: a=${c.io.a.peek().litValue}, b=${c.io.b.peek().litValue}, done=${c.io.done.peek().litToBoolean}")
        c.clock.step(1)
      }
      c.reset.poke(true.B)
      c.clock.step(1)
      c.reset.poke(false.B)
      // c.clock.step(1)
      c.io.a.poke(15.U)
      for (i <- 0 to 6) {
        println(s"Cycle $i: a=${c.io.a.peek().litValue}, b=${c.io.b.peek().litValue}, done=${c.io.done.peek().litToBoolean}")
        c.clock.step(1)
      }
    }
  }
}

class Foo2Test extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("Minimal CPU - foo2 Test")

  it should "foo2 foo2..." in {
    test(new my.Foo2) { c =>
      c.io.a.poke(5.U)
      for (i <- 0 to 6) {
        println(s"Cycle $i: a=${c.io.a.peek().litValue}, b=${c.io.b.peek().litValue}")
        c.clock.step(1)
      }
      c.reset.poke(true.B)
      c.clock.step()
      println(s"reset: a=${c.io.a.peek().litValue}, b=${c.io.b.peek().litValue}}")
      c.reset.poke(false.B)
      c.clock.step()
      c.io.a.poke(15.U)
      for (i <- 0 to 6) {
        println(s"Cycle $i: a=${c.io.a.peek().litValue}, b=${c.io.b.peek().litValue}}")
        c.clock.step(1)
      }
    }
  }
}