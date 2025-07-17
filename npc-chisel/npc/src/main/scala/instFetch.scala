package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._

class InstFetchIo(implicit val conf: ysyx_24100012_Config) extends Bundle() {
  val axi_port = new AXI4LiteIo
  val pipeline_kill = Input(Bool()) 
  val in = new InstFetchIn
  val pc_io = new PCOut()
  val valid = Output(Bool())
  val inst = Output(UInt(conf.xprlen.W))
  val finish = Input(Bool())
}

class InstFetchIn(implicit val conf: ysyx_24100012_Config) extends Bundle() {
  val pc_sel            =   Input(UInt(PC_4.getWidth.W))
  val br_target         =   Input(UInt(conf.xprlen.W))
  val jmp_target        =   Input(UInt(conf.xprlen.W))
  val jump_reg_target   =   Input(UInt(conf.xprlen.W))
  val exception_target  =   Input(UInt(conf.xprlen.W))
}


class PCOut(implicit val conf: ysyx_24100012_Config) extends Bundle() {
  val pc_plus4 = Output(UInt(conf.xprlen.W))
  val pc =  Output(UInt(conf.xprlen.W))
}

class ysyx_24100012_InstFetch(implicit conf: ysyx_24100012_Config) extends Module {
  val io = IO(new InstFetchIo())
  io := DontCare

  // Instruction Fetch
  val pc_next = Wire(UInt(conf.xprlen.W))

  // PC Register
  pc_next := MuxCase(io.in.pc_sel, Seq(
                    (io.in.pc_sel === PC_4)   -> io.pc_io.pc_plus4,
                    (io.in.pc_sel === PC_BR)  -> io.in.br_target,
                    (io.in.pc_sel === PC_J )  -> io.in.jmp_target,
                    (io.in.pc_sel === PC_JR)  -> io.in.jump_reg_target,
                    (io.in.pc_sel === PC_EXC) -> io.in.exception_target
                    ))

  val pc_reg = RegInit(START_ADDR)
  val pc_valid = RegInit(true.B)
  
  when(io.finish) {
      pc_reg := pc_next
      pc_valid := true.B
  } .otherwise {
      pc_valid := false.B
      pc_reg := pc_reg
  }
  
  val imem = Module(new ysyx_24100012_AXI4LiteMemeory())
  imem.io := DontCare
  imem.io.axi_port <> io.axi_port 
  // Memory Requests
  imem.io.port.req.valid := pc_valid
  imem.io.port.req.bits.addr := pc_reg
  imem.io.port.req.bits.fcn := M_XRD
  imem.io.port.req.bits.typ := MT_WU


  // Instruction Read
  val inst_reg = RegEnable(imem.io.port.resp.bits.data,imem.io.port.resp.valid)
  // val inst = Mux(imem.io.port.resp.valid,imem.io.port.resp.bits.data,inst_reg)

  io.inst := inst_reg
  // val pc_reg_reg = RegNext(pc_reg)
  io.pc_io.pc_plus4 := (pc_reg + 4.asUInt(conf.xprlen.W)) 
  io.pc_io.pc := pc_reg       

  // val valid = RegInit(false.B)
  val valid = RegNext(imem.io.port.resp.valid)
  val valid_reg = RegNext(valid)
  io.valid := valid_reg     
}