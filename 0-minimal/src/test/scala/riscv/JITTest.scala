// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FooTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("Minimal CPU - foo Test")

  it should "foo foo..." in {
    test(new my.Foo) { c =>
      c.clock.step(10)
      c.io.a.poke(3.U)
      c.io.valid.poke(true.B)
      for (i <- 0 to 6) {
        println(s"Cycle $i: a=${c.io.a.peek().litValue}, b=${c.io.b.peek().litValue}, done=${c.io.done.peek().litToBoolean}")
        c.clock.step(1)
      }
      c.clock.step(10)
      c.io.a.poke(23.U)
      c.io.valid.poke(true.B)
      for (i <- 0 to 6) {
        println(s"Cycle $i: a=${c.io.a.peek().litValue}, b=${c.io.b.peek().litValue}, done=${c.io.done.peek().litToBoolean}")
        c.clock.step(1)
      }
    }
  }
}

class FooTest2 extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("Minimal CPU - foo Test")

  it should "foo foo..." in {
    test(new my.Foo) { c =>
      c.clock.step(10)
      c.io.a.poke(3.U)
      c.io.valid.poke(true.B)
      c.clock.step(1)
      c.io.valid.poke(false.B)
      for (i <- 1 to 6) {
        println(s"Cycle $i: a=${c.io.a.peek().litValue}, b=${c.io.b.peek().litValue}, done=${c.io.done.peek().litToBoolean}")
        c.clock.step(1)
      }
      c.clock.step(10)
      c.io.a.poke(23.U)
      c.io.valid.poke(true.B)
      c.clock.step(1)
      c.io.valid.poke(false.B)
      for (i <- 1 to 6) {
        println(s"Cycle $i: a=${c.io.a.peek().litValue}, b=${c.io.b.peek().litValue}, done=${c.io.done.peek().litToBoolean}")
        c.clock.step(1)
      }
    }
  }
}
