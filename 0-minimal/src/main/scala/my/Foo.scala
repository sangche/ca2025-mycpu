package my

import chisel3._
import riscv.Parameters

class Foo extends Module {
  val io = IO(new Bundle {
    val a           = Input(UInt(Parameters.DataWidth))
    val b           = Output(UInt(Parameters.DataWidth)) 
    val done        = Output(Bool())
  })

  val counter = RegInit(0.U(3.W))
  
  counter := counter + 1.U

  io.done := (counter > 2.U)

  val x = RegNext(io.a) 
  val y = RegNext(x + 1.U)

  io.b := RegNext(y * 2.U)
}

class Foo2 extends Module {
  val io = IO(new Bundle {
    val a           = Input(UInt(Parameters.DataWidth))
    val b           = Output(UInt(Parameters.DataWidth)) 
  })

  // io.b := RegNext(RegNext(RegNext(io.a + 1.U)))

  // when (io.b === 0.U) {
  //   printf(p"Foo2: a=${io.a}, b=${io.b}\n")
  // }

  when (reset.asBool === true.B) {
    io.b := 0.U
  }.otherwise {
    io.b := RegNext(RegNext(RegNext(io.a + 1.U)))
  }

}