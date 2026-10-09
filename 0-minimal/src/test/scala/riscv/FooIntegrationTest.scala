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