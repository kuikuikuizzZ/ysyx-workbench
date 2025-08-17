package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._

class IFUDebugPort(implicit val conf: ysyx_24100012_Config)   extends Bundle() {
  val instFetchCount = Output(UInt(conf.perfCountBits.W))
}

class InstFetchIo(implicit val conf: ysyx_24100012_Config) extends Bundle() {
  val port          = new MemPortIo(conf.xlen)
  val pipeline_kill = Input(Bool()) 
  val in            = new InstFetchIn
  val pc_io         = new PCOut()
  val valid         = Output(Bool())
  val inst          = Output(UInt(conf.xprlen.W))
  val finish        = Input(Bool())
  val halt          = Input(Bool())
  val reset         = Input(Bool())
  val clock         = Input(Clock())
  val debug         = Output(new IFUDebugPort)

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
  val io = IO(
    new InstFetchIo()
  )
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
  
  when(io.finish && !io.halt) {
      pc_reg := pc_next
      pc_valid := true.B
  } .otherwise {
      pc_valid := false.B
      pc_reg := pc_reg
  } 
  
  // // Memory Requests
  // io.port.req.valid := pc_valid && !io.reset
  // io.port.req.bits.addr := pc_reg
  // io.port.req.bits.fcn := M_XRD
  // io.port.req.bits.typ := MT_WU

  // // Instruction Read
  // val inst_reg = RegEnable(io.port.resp.bits.data,BUBBLE,io.port.resp.valid)
  // val inst = Mux(io.port.resp.valid,io.port.resp.bits.data,inst_reg)

  // // val valid = RegInit(false.B)
  // val valid = RegNext(io.port.resp.valid,false.B)
  // // val valid_reg = RegNext(valid,false.B)
  // io.valid := valid     

  val cache = Module(new ysyx_24100012_ICache)
  val inst_reg = RegEnable(cache.io.inst,BUBBLE,cache.io.valid)
  cache.io.clock := clock
  cache.io.reset := reset
  cache.io.port <> io.port
  cache.io.pc := pc_reg
  cache.io.req_valid := pc_valid && !io.reset

  // val valid = RegInit(false.B)
  val valid = RegNext(cache.io.valid,false.B)
  // val valid_reg = RegNext(valid,false.B)
  
  
  io.valid := valid 
  io.inst := inst_reg
  io.pc_io.pc_plus4 := (pc_reg + 4.asUInt(conf.xprlen.W)) 
  io.pc_io.pc := pc_reg     
  ////////// debug
  val instFetchCount = RegInit(0.U(conf.perfCountBits.W))
  when(io.valid) {
    instFetchCount := instFetchCount + 1.U
  }
  io.debug.instFetchCount := instFetchCount
  ////////// end of debug 
}