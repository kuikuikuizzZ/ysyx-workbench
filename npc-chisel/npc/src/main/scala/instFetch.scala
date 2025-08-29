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

  // Instruction Fetch
  val pc_next = Wire(UInt(conf.xprlen.W))

  val pc_reg = RegInit(START_ADDR)
  val pc_valid = RegInit(true.B)

  when((!io.ctl.dec_stall && !io.ctl.full_stall) || io.ctl.pipeline_kill) {
      pc_reg := pc_next
      pc_valid := true.B
  } .otherwise {
      pc_valid := false.B
  }
  val pc_plus4 = (pc_reg + 4.asUInt(conf.xprlen.W))

  // PC Register
  pc_next :=  Mux(io.ctl.pc_sel === PC_4,         pc_plus4,
                 Mux(io.ctl.pc_sel === PC_BRJMP,  io.exu_in.exe_brjmp_target,
                 Mux(io.ctl.pc_sel === PC_JALR,   io.exu_in.exe_jump_reg_target,
                 /*Mux(io.ctl.pc_sel === PC_EXC*/ io.exception_target)))

  val cache       = Module(new ysyx_24100012_ICache)
  // val inst_reg    = RegEnable(cache.io.inst,BUBBLE,cache.io.valid)
  // val valid       = RegNext(cache.io.valid,false.B)
  cache.io.clock := clock
  cache.io.reset := reset
  cache.io.port <> io.port
  cache.io.pc := pc_reg
  cache.io.req_valid := !io.reset && pc_valid 
  cache.io.debug <> io.debug.icache 
  
  // Pipeline Interface
  val if_inst = cache.io.inst
  io.icache_valid := cache.io.valid 
  when (io.ctl.pipeline_kill)
  {
    io.ifu_dec.valid := false.B
    io.ifu_dec.bits.inst := BUBBLE
  }
  .elsewhen (!io.ctl.dec_stall && !io.ctl.full_stall)
  {
    when (io.ctl.if_kill)
    {
        io.ifu_dec.valid := false.B
        io.ifu_dec.bits.inst := BUBBLE
    }
    .otherwise
    {
        io.ifu_dec.valid := cache.io.valid
        io.ifu_dec.bits.inst := if_inst
    }

    io.ifu_dec.bits.pc := pc_reg
  }



  ////////// debug
  val instFetchCount = RegInit(0.U(conf.perfCountBits.W))
  when(cache.io.valid) {
    instFetchCount := instFetchCount + 1.U
  }
  io.debug.valid := cache.io.valid
  io.debug.instFetchCount := instFetchCount
  ////////// end of debug 
}