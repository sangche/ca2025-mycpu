package riscv

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import java.nio.file.Files

class FooIntegrationTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "CPU + Foo integration"

  it should "execute FOO with x5=3 and write 8 to x10" in {
    // Program:
    //   addi x5, x0, 3
    //   .insn r 0x0b, 0, 0, x10, x5, x0  # FOO x10, x5
    //   addi x0, x0, 0                   # NOP
    //
    // Each instruction is encoded as a 32-bit little-endian word.
    val program = Array[Byte](
      // addi x5, x0, 3 = 0x00300293
      0x93.toByte, 0x02.toByte, 0x30.toByte, 0x00.toByte,

      // FOO x10, x5 = 0x0002850b
      0x0b.toByte, 0x85.toByte, 0x02.toByte, 0x00.toByte,

      // addi x0, x0, 0 = 0x00000013
      0x13.toByte, 0x00.toByte, 0x00.toByte, 0x00.toByte
    )

    val image = Files.createTempFile("foo-test-", ".asmbin")
    Files.write(image, program)

    try {
      test(new TestTopModule(image.toAbsolutePath.toString)) { c =>
        // 0 means no clock timeout
        c.clock.setTimeout(0)

        // Allow the ROM loader and CPU to complete execution.
        c.clock.step(50000)

        // Check that the source register was initialized correctly.
        c.io.regs_debug_read_address.poke(5.U)
        c.clock.step()
        c.io.regs_debug_read_data.expect(3.U)

        // Check the Foo result written to x10:
        // (x5 + 1) * 2 = (3 + 1) * 2 = 8
        c.io.regs_debug_read_address.poke(10.U)
        c.clock.step()
        c.io.regs_debug_read_data.expect(8.U)
      }
    } finally {
      Files.deleteIfExists(image)
    }
  }
}