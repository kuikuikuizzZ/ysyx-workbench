package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._

class IFUDebugPort(implicit val conf: ysyx_24100012_Config)   extends Bundle() {
  val valid           = Output(Bool())
  val instFetchCount  = Output(UInt(conf.perfCountBits.W))
  val icache          = new ICacheDebugPort
}

class IFUPipeIO(implicit val conf: ysyx_24100012_Config) extends Bundle {
  val pc        = Output(UInt(conf.xprlen.W))
  val inst      = Output(UInt(conf.xprlen.W))
  val pc_valid         = Output(Bool())
   val exception        = Output(UInt(EXC_NORMAL.getWidth.W))
}

class InstFetchIo(implicit val conf: ysyx_24100012_Config) extends Bundle() {
  val clock             = Input(Clock())
  val reset             = Input(Bool())
  val ctl               = new CtrlSignalIO
  val port              = new MemPortIo(conf.xlen)
  val exu_in            = Flipped(new EXUToIFUOut)
  val ifu_dec          = new DecoupledIO(new IFUPipeIO())
  val exception_target  = Input(UInt(conf.xprlen.W))
  val debug             = Output(new IFUDebugPort)
  val icache_valid      = Output(Bool())
}


class ysyx_24100012_InstFetch(implicit conf: ysyx_24100012_Config) extends Module {
  val io = IO(
    new InstFetchIo()
  )
  io := DontCare
  val cache       = Module(new ysyx_24100012_ICache)

  // Instruction Fetch
  val pc_next = Wire(UInt(conf.xprlen.W))

  val pc_reg = RegInit(START_ADDR)
  val pc_valid = RegInit(true.B)
  val should_kill = io.ctl.if_kill || io.ctl.pipeline_kill
  val inst = Mux(should_kill, BUBBLE,cache.io.inst)
  val if_inst = Mux(cache.io.valid,cache.io.inst,RegEnable(inst,BUBBLE,cache.io.valid || io.ifu_dec.ready || io.ctl.if_kill ))
  val if_valid = Mux(cache.io.valid,cache.io.valid,RegEnable(cache.io.valid && !io.ifu_dec.ready,cache.io.valid || io.ifu_dec.ready || io.ctl.if_kill))
  when((if_valid && io.ifu_dec.ready) || should_kill) {
      pc_reg := pc_next
      pc_valid := true.B
  }
  .otherwise {
      pc_valid := false.B
  }
  val pc_plus4 = (pc_reg + 4.asUInt(conf.xprlen.W))

  // PC Register
  pc_next :=  Mux(io.ctl.exe_pc_sel     === PC_4,         pc_plus4,
                 Mux(io.ctl.exe_pc_sel  === PC_BRJMP,  io.exu_in.exe_brjmp_target,
                 Mux(io.ctl.exe_pc_sel  === PC_JALR,   io.exu_in.exe_jump_reg_target,
                 /*Mux(io.ctl.pc_sel === PC_EXC*/ io.exception_target)))

  // val inst_reg    = RegEnable(cache.io.inst,BUBBLE,cache.io.valid)
  // val valid       = RegNext(cache.io.valid,false.B)

  // NOTE: when if_kill, should not take the old pc value
  cache.io.req_valid  := !io.reset && pc_valid && !should_kill 
  cache.io.clock      := clock
  cache.io.reset      := reset
  cache.io.pc         := pc_reg
  cache.io.port       <> io.port
  cache.io.debug      <> io.debug.icache 
  

  // NOTE: if_kill should clean inst, in ifu_dec reg
  io.ifu_dec.valid :=   Mux(should_kill || cache.io.exception =/= EXC_NORMAL , true.B, if_valid)
  io.ifu_dec.bits.inst :=  Mux(should_kill || cache.io.exception =/= EXC_NORMAL, BUBBLE,if_inst)
  io.ifu_dec.bits.pc := pc_reg
  io.ifu_dec.bits.pc_valid := Mux(should_kill|| cache.io.exception =/= EXC_NORMAL, false.B, true.B)
  io.ifu_dec.bits.exception := cache.io.exception
  io.icache_valid := cache.io.valid
  

  ////////// debug
  val instFetchCount = RegInit(0.U(conf.perfCountBits.W))
  when(cache.io.valid) {
    instFetchCount := instFetchCount + 1.U
  }
  io.debug.valid := cache.io.valid
  io.debug.instFetchCount := instFetchCount
  ////////// end of debug 
}