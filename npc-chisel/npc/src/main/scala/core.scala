package npc


import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._
import npc.devices.{ysyx_24100012_AXI4CLINT}

class CoreIo(implicit val conf: ysyx_24100012_Config) extends Bundle 
{
  val interrupt = Input(Bool())
  val master = new AXI4LiteIo()
  val slave = Flipped(new AXI4LiteIo())
}

class ysyx_24100012 extends Module
{
  implicit val conf = ysyx_24100012_Config()

  val io = IO(new CoreIo())


  val inst_fetch = Module(new ysyx_24100012_InstFetch())
  val arbiter = Module(new ysyx_24100012_AXI4LiteArbiter(2))
  val c  = Module(new ysyx_24100012_Decoder())
  val d  = Module(new ysyx_24100012_EXU())
  val reg_file = Module(new ysyx_24100012_RegFile())
  val lsu = Module(new ysyx_24100012_LSU())
  val wbu = Module(new ysyx_24100012_WBU())
  val clint = Module(new ysyx_24100012_AXI4CLINT())

  clint.io.axi_io <> io.slave
  clint.io.clock := clock
  clint.io.reset := reset

  arbiter.io := DontCare
  arbiter.io.axi_port <> io.master
  arbiter.io.ifu_valid := inst_fetch.io.valid
  arbiter.io.mem_en := lsu.io.ctl.mem_en
  arbiter.io.ports(DPORT) <> lsu.io.port  
  arbiter.io.ports(IPORT) <> inst_fetch.io.port 

  inst_fetch.io.clock := clock
  inst_fetch.io.reset := reset
  inst_fetch.io.in <> d.io.targets
  inst_fetch.io.pipeline_kill := c.io.pipeline_kill
  inst_fetch.io.finish := c.io.finish

  c.io := DontCare
  c.io.ctl  <> d.io.ctl
  c.io.inst := inst_fetch.io.inst
  c.io.ifu_valid := inst_fetch.io.valid 
  c.io.ls_valid := lsu.io.ls_valid
  c.io.pc_io <> inst_fetch.io.pc_io

  reg_file.io.inst := inst_fetch.io.inst
  reg_file.io.wb <> wbu.io.reg

  d.io.reg_in <> reg_file.io.out
  d.io.pc_io <> inst_fetch.io.pc_io
  d.io.inst := inst_fetch.io.inst
  d.io.ifu_valid := inst_fetch.io.valid

  lsu.io.exe <> d.io.exe_lsu  
  lsu.io.ctl <> c.io.ctl_lsu
  lsu.io.pc_io <> inst_fetch.io.pc_io
  
  
  wbu.io.ctl <> c.io.ctl_wb
  wbu.io.exe <> d.io.exe_wbu
  wbu.io.lsu <> lsu.io.wb

  // io.halt :=  d.io.ebreak would lead to conflicts in same cycle
  val halt = Mux(d.io.ebreak, true.B, false.B)


  /////// debug port
  val debug = Module(new debug_port())
  debug.io.clock := clock
  debug.io.reset := reset
  debug.io.halt := halt
  debug.io.pc := inst_fetch.io.pc_io.pc
  debug.io.inst := inst_fetch.io.inst
}


