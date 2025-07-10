package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._

class InstFetchIo(implicit val conf: YSYX24100012Config) extends Bundle() {
  val imem = new MemPortIo(conf.xprlen)
  val pipeline_kill = Input(Bool()) 
  val in = new InstFetchIn
  val pc_io = new PCOut()
  val stall = Input(Bool())
  val inst = Output(UInt(conf.xprlen.W))
  // val vaild = Output(Bool())
}

class InstFetchIn(implicit val conf: YSYX24100012Config) extends Bundle() {
  val pc_sel            =   Input(UInt(PC_4.getWidth.W))
  val br_target         =   Input(UInt(conf.xprlen.W))
  val jmp_target        =   Input(UInt(conf.xprlen.W))
  val jump_reg_target   =   Input(UInt(conf.xprlen.W))
  val exception_target  =   Input(UInt(conf.xprlen.W))
}


class PCOut(implicit val conf: YSYX24100012Config) extends Bundle() {
  val pc_plus4 = Output(UInt(conf.xprlen.W))
  val pc =  Output(UInt(conf.xprlen.W))
}

class YSYX24100012InstFetch(implicit conf: YSYX24100012Config) extends Module {
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
  when(io.imem.resp.valid && !io.pipeline_kill) {
      pc_reg := pc_next
  }

  // Memory Requests
  io.imem.req.valid := true.B
  io.imem.req.bits.addr := pc_reg
  io.imem.req.bits.fcn := M_XRD
  io.imem.req.bits.typ := MT_WU


  // Instruction Read
  val inst = Mux(io.imem.resp.valid,io.imem.resp.bits.data,BUBBLE)
  // val reg_inst = Reg(UInt(conf.xlen.W))
  // val reg_pc_old = RegInit(START_ADDR)

  // when (io.pipeline_kill && !io.imem.resp.valid){
  //   reg_inst := BUBBLE
  // } .otherwise {
  //   reg_inst := inst
  //   reg_pc_old := pc_reg
  // }
  io.inst := inst
  io.pc_io.pc_plus4 := (pc_reg + 4.asUInt(conf.xprlen.W)) 
  io.pc_io.pc := pc_reg  
           
}