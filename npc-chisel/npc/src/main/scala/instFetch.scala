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

class InstFetchIn(implicit val conf: ysyx_24100012_Config) extends Bundle() {
  val pc_sel              =   Input(UInt(PC_4.getWidth.W))
  val dec_stall           =   Input(Bool())
  val full_stall          =   Input(Bool())
  val pipeline_kill       =   Input(Bool())
  val if_kill              =   Input(Bool())
  val dec_kill            =   Input(Bool())
}

class InstFetchIo(implicit val conf: ysyx_24100012_Config) extends Bundle() {
  val clock             = Input(Clock())
  val reset             = Input(Bool())
  val ctl               = new InstFetchIn
  val port              = new MemPortIo(conf.xlen)
  val exu_in            = Flipped(new EXUToIFUOut)
  val ifu_pipe          = new DecoupledIO(new IFUPipeIO())
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
  
  when((!io.ctl.dec_stall && !io.ctl.full_stall) || io.ctl.pipeline_kill) {
      pc_reg := pc_next
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
  cache.io.req_valid := !io.reset
  cache.io.debug <> io.debug.icache 
  
  // Pipeline Interface
  io.icache_valid = cache.io.valid
  io.ifu_pipe.bits.pc    := pc_reg
  io.ifu_pipe.bits.inst  := cache.io.inst
   when (io.ctl.pipeline_kill)
   {
      io.ifu_pipe.valid := false.B
      dec_reg_inst := BUBBLE
   }
   .elsewhen (!io.ctl.dec_stall && !io.ctl.full_stall)
   {
      when (io.ctl.if_kill)
      {
         io.ifu_pipe.valid := false.B
         dec_reg_inst := BUBBLE
      }
      .otherwise
      {
         io.ifu_pipe.valid := true.B
         dec_reg_inst := if_inst
      }

      dec_reg_pc := if_reg_pc
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