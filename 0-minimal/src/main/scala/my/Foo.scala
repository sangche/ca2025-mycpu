package my

import chisel3._
import riscv.Parameters

class Foo extends Module {
  val io = IO(new Bundle {
    val a           = Input(UInt(Parameters.DataWidth))
    val b           = Output(UInt(Parameters.DataWidth)) 
    val done        = Output(Bool())
  })

  
}