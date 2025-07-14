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
  val lsu_stall = Input(Bool()) 
  val valid = Output(Bool())
  val inst = Output(UInt(conf.xprlen.W))
  val finish = Input(Bool())
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
  val pc_old =  Output(UInt(conf.xprlen.W))
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
  val pc_valid = RegInit(true.B)
  
  when(io.finish) {
      pc_reg := pc_next
      pc_valid := true.B
  } .otherwise {
      pc_valid := false.B
      pc_reg := pc_reg
  }
  
  // Memory Requests
  io.imem.req.valid := pc_valid
  io.imem.req.bits.addr := pc_reg
  io.imem.req.bits.fcn := M_XRD
  io.imem.req.bits.typ := MT_WU


  // Instruction Read
  val inst_reg = RegEnable(io.imem.resp.bits.data,io.imem.resp.valid)
  val inst = Mux(io.imem.resp.valid, io.imem.resp.bits.data, inst_reg)

  val pc_old = RegNext(pc_reg)
  io.inst := inst
  io.pc_io.pc_plus4 := (pc_reg + 4.asUInt(conf.xprlen.W)) 
  io.pc_io.pc := pc_reg       
  io.pc_io.pc_old := pc_old

  // val valid = RegInit(false.B)
  val valid = RegNext(io.imem.resp.valid)
  io.valid := valid     
}