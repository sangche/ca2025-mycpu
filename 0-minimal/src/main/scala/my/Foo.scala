package my

import chisel3._
import riscv.Parameters

class Foo extends Module {
  val io = IO(new Bundle {
    val a     = Input(UInt(Parameters.DataWidth))
    val valid = Input(Bool())
    val b     = Output(UInt(Parameters.DataWidth))
    val done   = Output(Bool())
  })

  // Cycle 1
  // valid가 들어온 입력을 첫 번째 pipeline register에 저장한다.
  val x = RegNext(Mux(io.valid, io.a, 0.U))

  // Cycle 2
  // +1
  val y = RegNext(x + 1.U)

  // Cycle 3
  // *2
  io.b := RegNext(y * 2.U)

  // valid를 3 cycle delay하여 done으로 출력한다.
  val valid_d1 = RegNext(io.valid)
  val valid_d2 = RegNext(valid_d1)
  val valid_d3 = RegNext(valid_d2)

  io.done := valid_d3
}