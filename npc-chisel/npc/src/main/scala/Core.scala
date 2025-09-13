package npc


import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._
import npc.devices.{CLINT}

  

class CoreIo(implicit val conf: Config) extends Bundle 
{
  val interrupt = Input(Bool())
  val master = new AXI4LiteIo()
  val slave = Flipped(new AXI4LiteIo())
  val halt = Output(Bool())
}

class Core(implicit val conf: Config)extends Module
{
  def pipelineConnect[T <: Data, T2 <: Data](prevOut: DecoupledIO[T],
    thisIn: DecoupledIO[T], thisOut: DecoupledIO[T2]) = {
      prevOut.ready := thisIn.ready
      thisIn.bits := RegEnable(prevOut.bits,0,prevOut.valid && thisIn.ready )
      // thisIn.bits := RegNext(prevOut.bits)
      thisIn.valid := (prevOut.valid && thisIn.ready)
  }
  val io = IO(new CoreIo())

  val inst_fetch  = Module(new InstFetch())
  val arbiter     = Module(new AXI4LiteArbiter(2))
  val decoder     = Module(new Decoder())
  val reg_file    = Module(new RegFile())
  val exu         = Module(new EXU())
  val lsu         = Module(new LSU())
  val wbu         = Module(new WBU())
  val clint       = Module(new CLINT())



  clint.io.clock := clock
  clint.io.reset := reset
  clint.io.in <> lsu.io.clintIO  

  arbiter.io.axi_port <> io.master
  arbiter.io.ports(DPORT) <> lsu.io.port  
  arbiter.io.ports(IPORT) <> inst_fetch.io.port 

  inst_fetch.io.ctl <> decoder.io.ctl_sign
  inst_fetch.io.exception_target := lsu.io.exception_target
  inst_fetch.io.exu_in <>  exu.io.ifu_out
  
  decoder.io.reg_in <> reg_file.io.out
  decoder.io.dec_reg <> reg_file.io.dec
  decoder.io.icache_valid := inst_fetch.io.icache_valid 

  exu.io.ctl <> decoder.io.ctl_sign
  exu.io.to_ctl <> decoder.io.exe_ctl 

  lsu.io.ctl <> decoder.io.ctl_lsu
  lsu.io.to_ctl <> decoder.io.lsu_ctl

  wbu.io.ctl <> decoder.io.ctl_sign
  wbu.io.reg <> reg_file.io.wb
  wbu.io.to_ctl <> decoder.io.wb_ctl

  pipelineConnect(inst_fetch.io.ifu_dec, decoder.io.ifu_dec, decoder.io.dec_exe)
  pipelineConnect(decoder.io.dec_exe, exu.io.dec_exe, exu.io.exe_mem)
  pipelineConnect(exu.io.exe_mem, lsu.io.exe_mem, lsu.io.mem_wb)
  pipelineConnect(lsu.io.mem_wb, wbu.io.mem_wb, wbu.io.reg)

  // io.halt :=  exu.io.ebreak would lead to conflicts in same cycle
  val halt = Mux(wbu.io.ebreak, true.B, false.B)
  io.slave.ar.ready := false.B
  io.slave.r.data := 0.U
  io.slave.r.resp := 0.U
  io.slave.r.valid := false.B
  io.slave.r.last := false.B
  io.slave.r.id := 0.U
  io.slave.aw.ready := false.B
  io.slave.w.ready := false.B
  io.slave.b.valid := false.B
  io.slave.b.resp := 0.U
  io.slave.b.id := 0.U
  io.halt := halt 

  // ///// debug port
  if (conf.ENABLE_DEBUG) {
    val debug = Module(new DebugPort())
    val perfEvent = Module(new PerfEventPort())
    debug.io.clock := clock
    debug.io.reset := reset
    debug.io.halt := halt
    debug.io.pc := inst_fetch.io.ifu_dec.bits.pc
    debug.io.mem_pc := lsu.io.mem_wb.bits.pc
    debug.io.wb_pc := wbu.io.wb_pc 
    debug.io.inst := exu.io.dec_exe.bits.inst
    debug.io.wb_inst := wbu.io.wb_inst
    debug.io.wb_valid := wbu.io.mem_wb.bits.mem_resp_valid
    debug.io.lsu_port := wbu.io.mem_wb.bits.debug
    
    perfEvent.io.clock      := clock
    perfEvent.io.reset      := reset
    perfEvent.io.ifu_port   := inst_fetch.io.debug
    perfEvent.io.ctl_port   := decoder.io.debug
    perfEvent.io.lsu_port   := lsu.io.debug
    perfEvent.io.wbu_port   := wbu.io.debug
  }
}


