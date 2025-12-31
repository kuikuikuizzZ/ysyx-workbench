package npc.galois


import chisel3._
import chisel3.util._
import npc._
import npc.common._
import npc.galois._
import npc.galois.Constants._
import npc.devices.{CLINT}

class Galois extends Module { 
  implicit val conf = Config()
  val io = IO(new CoreIo())

  dontTouch(io)
  val core = Module(new Core())
  chisel3.experimental.annotate(
    new chisel3.experimental.ChiselAnnotation {
      override def toFirrtl = sifive.enterprise.firrtl
        .NestedPrefixModulesAnnotation(core.toTarget, "ysyx_24100012_", true)
    }
  )
  core.io.core <> io
  val halt = RegInit(false.B)
  halt := core.io.ebreak
  dontTouch(halt)

}

class CoreIo(implicit val conf: Config) extends Bundle 
{
  val interrupt = Input(Bool())
  val master = new AXI4Io()
  val slave = Flipped(new AXI4LiteIo())
}

class Core(implicit val conf: Config)extends Module
{
  def pipelineConnect[T <: Data, T2 <: Data](prevOut: DecoupledIO[T],
    thisIn: DecoupledIO[T]) = {
      prevOut.ready := thisIn.ready
      thisIn.bits := RegEnable(prevOut.bits,0.U.asTypeOf(chiselTypeOf(prevOut.bits)),prevOut.valid && thisIn.ready )
      thisIn.valid := prevOut.valid && thisIn.ready
  }
  val io = IO(new Bundle {
    val ebreak = Output(Bool())
    val core =  new CoreIo()
  })
  val inst_fetch  = Module(new InstFetch())
  val decoder     = Module(new Decode())
  val rm          = Module(new RegMap())
  val dp          = Module(new Dispatch())
  val rr          = Module(new RegRead())
  val exu         = Module(new EXU())
  val lsu         = Module(new LSUImpl())
  val cmt         = Module(new Commit())
  val clint       = Module(new CLINT())
  val axi_master  = Module(new AXI4Master())
  val axi_arb     = Module(new RRArbiter(new AXI4Req(),2))
  clint.io.clock := clock
  clint.io.reset := reset
  clint.io.in <> lsu.io.clintIO  

  // arbiter.io.axi_port <> io.core.master
  axi_arb.io.in(DPORT) <> lsu.io.axi_bus.req
  axi_arb.io.in(IPORT) <> inst_fetch.io.axi_bus.req

  axi_master.io := DontCare
  axi_arb.io.out.ready    := axi_master.io.req.ready
  axi_master.io.req.valid := axi_arb.io.out.valid  
  axi_master.io.req.bits  := axi_arb.io.out.bits
  axi_master.io.axi_io <> io.core.master
  axi_master.io.resp <> lsu.io.axi_bus.resp
  axi_master.io.resp <> inst_fetch.io.axi_bus.resp

  inst_fetch.io.redirect  := cmt.io.redirect
  inst_fetch.io.retireA   := cmt.io.retireA
  // inst_fetch.io.debug     := DontCare

  decoder.io.redirect := cmt.io.redirect
  decoder.io.debug     := DontCare

  rm.io.redirect  := cmt.io.redirect
  rm.io.cmtA      := rr.io.cmtA
  rm.io.cmtB      := rr.io.cmtB
  rm.io.cmtC      := exu.io.exe_out.instA
  rm.io.cmtD      := exu.io.exe_out.instB
  rm.io.cmtE      := lsu.io.cmtE
  rm.io.rob_numA  := cmt.io.rob_numA
  rm.io.rob_numB  := cmt.io.rob_numB
  rm.io.retireA   := cmt.io.retireA
  rm.io.retireB   := cmt.io.retireB
  // ? = rm.io.arch_regfile

  dp.io.redirect      := cmt.io.redirect
  dp.io.phyreg_states := rm.io.phyreg_states

  rr.io.redirect  := cmt.io.redirect
  rr.io.cmtC      := exu.io.exe_out.instA
  rr.io.cmtD      := exu.io.exe_out.instB
  rr.io.cmtE      := lsu.io.cmtE

  exu.io.redirect := cmt.io.redirect
  exu.io.debug    := DontCare

  lsu.io.redirect       := cmt.io.redirect
  lsu.io.retire_store   <> cmt.io.retire_store
  lsu.io.forward_store  := cmt.io.forward_store
  lsu.io.debug          := DontCare

  cmt.io.cmtA           := rr.io.cmtA
  cmt.io.cmtB           := rr.io.cmtB
  cmt.io.cmtC           := exu.io.exe_out.instA
  cmt.io.cmtD           := exu.io.exe_out.instB
  cmt.io.cmtE           := lsu.io.cmtE
  cmt.io.cmtF           := exu.io.exe_csr
  cmt.io.forward_load   := lsu.io.forward_load
  cmt.io.rm_rob         <> rm.io.rm_dp

  io.ebreak := cmt.io.retireA.valid && (cmt.io.retireA.csr_ctrl.ebreak)
  dontTouch(io.ebreak)

  pipelineConnect(inst_fetch.io.ifu_dec, decoder.io.ifu_dec)
  pipelineConnect(decoder.io.dec_rm, rm.io.dec_rm)
  pipelineConnect(rm.io.rm_dp, dp.io.rm_dp)
  pipelineConnect(dp.io.dp_rr, rr.io.dp_rr)
  pipelineConnect(dp.io.dp_rr_mem, rr.io.dp_rr_mem)
  pipelineConnect(rr.io.rr_exe, exu.io.rr_exe)
  pipelineConnect(rr.io.rr_exe_mem, exu.io.rr_exe_mem)
  pipelineConnect(exu.io.exe_mem, lsu.io.exe_mem)
  pipelineConnect(cmt.io.retire_store, lsu.io.retire_store )
  // io.halt :=  exu.io.ebreak would lead to conflicts in same cycle
  val halt = Mux(io.ebreak, true.B, false.B)
  io.core.slave.ar.ready := false.B
  io.core.slave.r.data := 0.U
  io.core.slave.r.resp := 0.U
  io.core.slave.r.valid := false.B
  io.core.slave.r.last := false.B
  io.core.slave.r.id := 0.U
  io.core.slave.aw.ready := false.B
  io.core.slave.w.ready := false.B
  io.core.slave.b.valid := false.B
  io.core.slave.b.resp := 0.U
  io.core.slave.b.id := 0.U
  io.core.master.ar.bits.id := 0.U

  // ///// debug port
  // if (conf.ENABLE_DEBUG) {
  //   val debug = Module(new DebugPort())
  //   val perfEvent = Module(new PerfEventPort())
  //   debug.io.clock := clock
  //   debug.io.reset := reset
  //   debug.io.halt := halt
  //   debug.io.pc := inst_fetch.io.ifu_dec.bits.pc
  //   debug.io.mem_pc := lsu.io.mem_wb.bits.pc
  //   debug.io.wb_pc := wbu.io.wb_pc 
  //   debug.io.inst := inst_fetch.io.ifu_dec.bits.inst
  //   debug.io.wb_valid := wbu.io.mem_wb.bits.mem_resp_valid
  //   debug.io.lsu_port := wbu.io.mem_wb.bits.debug
  //   debug.io.wb_inst := 0.U
    
  //   perfEvent.io.clock      := clock
  //   perfEvent.io.reset      := reset
  //   perfEvent.io.ifu_port   := inst_fetch.io.debug
  //   perfEvent.io.ctl_port   := decoder.io.debug
  //   perfEvent.io.lsu_port   := lsu.io.debug
  //   perfEvent.io.wbu_port   := wbu.io.debug
  //   perfEvent.io.exu_port   := exu.io.debug
  // }
}


