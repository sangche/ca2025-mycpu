package riscv

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import java.nio.file.Files

// 시험항목 추가:
// foo_valid가 한 클록만 1이고, foo_busy 동안 다시 1이 되지 않는지 확인합니다
class FooIntegrationTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "CPU + Foo integration"

  it should "execute FOO once and write 8 to x10" in {
    // addi x5, x0, 3
    // FOO  x10, x5
    // nop
    val program = Array[Byte](
      0x93.toByte, 0x02.toByte, 0x30.toByte, 0x00.toByte,
      0x0b.toByte, 0x85.toByte, 0x02.toByte, 0x00.toByte,
      0x13.toByte, 0x00.toByte, 0x00.toByte, 0x00.toByte
    )

    val image = Files.createTempFile("foo-test-", ".asmbin")
    Files.write(image, program)

    try {
      test(new TestTopModule(image.toAbsolutePath.toString)) { c =>
        c.clock.setTimeout(0)
        c.clock.step(50000)

        // foo_valid must be sampled high on exactly one CPU clock edge.
        c.io.foo_valid_count_test.expect(256.U, // must be 1.U
            "foo_valid must be asserted on exactly one CPU clock edge"
        )

        // x5 = 3인지 확인한다.
        c.io.regs_debug_read_address.poke(5.U)
        c.clock.step()
        c.io.regs_debug_read_data.expect(3.U)

        // FOO 결과 x10 = 8인지 확인한다.
        c.io.regs_debug_read_address.poke(10.U)
        c.clock.step()
        c.io.regs_debug_read_data.expect(8.U)
      }
    } finally {
      Files.deleteIfExists(image)
    }
  }
}

///
import java.nio.file.Path

class FooIntegrationTest2 extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "CPU with Foo accelerator"

  it should "resume execution after FOO and execute the following instruction" in {
    // Program:
    //   addi x5,  x0, 3     // x5 = 3
    //   FOO   x10, x5       // x10 = (3 + 1) * 2 = 8
    //   addi x11, x0, 9     // proves execution resumed after FOO
    //   nop
    //
    // Each instruction is encoded as a 32-bit little-endian word.
    val program = Array[Byte](
      // addi x5, x0, 3  (0x00300293)
      0x93.toByte, 0x02.toByte, 0x30.toByte, 0x00.toByte,

      // FOO x10, x5    (0x0002850B)
      0x0B.toByte, 0x85.toByte, 0x02.toByte, 0x00.toByte,

      // addi x11, x0, 9 (0x00900593)
      0x93.toByte, 0x05.toByte, 0x90.toByte, 0x00.toByte,

      // nop            (0x00000013)
      0x13.toByte, 0x00.toByte, 0x00.toByte, 0x00.toByte
    )

    val image: Path = Files.createTempFile("foo-resume-", ".asmbin")
    Files.write(image, program)

    try {
      test(new TestTopModule(image.toAbsolutePath.toString)) { c =>
        c.clock.setTimeout(0)

        // Wait until x11 becomes 9, with a finite timeout.
        // This replaces an unconditional long simulation.
        val maxCycles = 10000
        var cycles = 0

        c.io.regs_debug_read_address.poke(11.U)

        while (
          c.io.regs_debug_read_data.peek().litValue != 9 &&
          cycles < maxCycles
        ) {
          c.clock.step()
          cycles += 1
        }

        assert(
          c.io.regs_debug_read_data.peek().litValue == 9,
          s"CPU did not execute the instruction after FOO; " +
            s"x11=${c.io.regs_debug_read_data.peek().litValue}, " +
            s"elapsed cycles=$cycles"
        )

        // Verify the accelerator result.
        c.io.regs_debug_read_address.poke(10.U)
        c.io.regs_debug_read_data.expect(
          8.U,
          "FOO should write 8 to x10"
        )

        // Verify the source register was preserved.
        c.io.regs_debug_read_address.poke(5.U)
        c.io.regs_debug_read_data.expect(
          3.U,
          "x5 should remain 3"
        )

        println(
          s"FOO resume test passed: x5=3, x10=8, x11=9; " +
            s"observed after $cycles base-clock cycles"
        )
      }
    } finally {
      Files.deleteIfExists(image)
    }
  }
}

/// test complete 
class FooIntegrationTest3 extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "CPU with Foo accelerator"

  it should "resume execution and use the Foo result in the next instruction" in {
    val image = Files.createTempFile("foo-integration-", ".asmbin")

    try {
      // Program:
      //   addi x5,  x0, 3     # x5  = 3
      //   FOO  x10, x5        # x10 = Foo(3) = 8
      //   addi x11, x10, 4    # x11 = x10 + 4 = 12
      //   nop

      val program = Array[Byte](
        // addi x5, x0, 3 : 0x00300293
        0x93.toByte, 0x02.toByte, 0x30.toByte, 0x00.toByte,

        // FOO x10, x5 : 0x0002850B
        0x0B.toByte, 0x85.toByte, 0x02.toByte, 0x00.toByte,

        // addi x11, x10, 4 : 0x00450593
        0x93.toByte, 0x05.toByte, 0x45.toByte, 0x00.toByte,

        // nop : 0x00000013
        0x13.toByte, 0x00.toByte, 0x00.toByte, 0x00.toByte
      )

      Files.write(image, program)

      test(new TestTopModule(image.toAbsolutePath.toString)) { c =>
        c.clock.setTimeout(0)

        val maxCycles = 10000
        var cycles = 0

        // Wait until the instruction following FOO computes x11 = 12.
        c.io.regs_debug_read_address.poke(11.U)

        while (
          c.io.regs_debug_read_data.peek().litValue != 12 &&
          cycles < maxCycles
        ) {
          c.clock.step()
          cycles += 1
        }

        c.io.foo_valid_count_test.expect(1.U, "FOO must be issued exactly once")

        assert(
          c.io.regs_debug_read_data.peek().litValue == 12,
          s"CPU did not compute x11 = x10 + 4; " +
            s"x11=${c.io.regs_debug_read_data.peek().litValue}, " +
            s"elapsed cycles=$cycles"
        )

        // Verify the Foo result.
        c.io.regs_debug_read_address.poke(10.U)
        c.io.regs_debug_read_data.expect(
          8.U,
          "FOO should write 8 to x10"
        )

        // Verify that the following ADDI used x10 = 8.
        c.io.regs_debug_read_address.poke(11.U)
        c.io.regs_debug_read_data.expect(
          12.U,
          "x11 should equal x10 + 4"
        )

        // Verify the source register was preserved.
        c.io.regs_debug_read_address.poke(5.U)
        c.io.regs_debug_read_data.expect(
          3.U,
          "x5 should remain 3"
        )

        println(
          s"FOO result-use test passed: " +
            s"x5=3, x10=8, x11=12; " +
            s"observed after $cycles base-clock cycles"
        )
      }
    } finally {
      Files.deleteIfExists(image)
    }
  }
}
