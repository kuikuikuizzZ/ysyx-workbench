package npc


import chisel3._
import chisel3.util._
import npc.common.{YSYX24100012Config, MemPortIo}

class CoreIo(implicit val conf: YSYX24100012Config) extends Bundle 
{
  val imem = new MemPortIo(conf.xprlen)
  val dmem = new MemPortIo(conf.xprlen)
  val halt = Output(Bool())
}

class Core(implicit val conf: YSYX24100012Config) extends Module
{
  val io = IO(new CoreIo())
  io := DontCare
  val inst_fetch = Module(new YSYX24100012InstFetch())
  val c  = Module(new YSYX24100012Cpath())
  val d  = Module(new YSYX24100012Dpath())
  val reg_file = Module(new RegFile())
  val lsu = Module(new YSYX2400012LSU())
  val wbu = Module(new YSYX2400012WBU())
  
  inst_fetch.io.in <> d.io.targets
  inst_fetch.io.pipeline_kill := c.io.pipeline_kill
  io.imem <> inst_fetch.io.imem
  inst_fetch.io.stall := lsu.io.stall
  
  c.io.ctl  <> d.io.ctl
  c.io.inst := inst_fetch.io.inst

  reg_file.io.inst := inst_fetch.io.inst
  reg_file.io.wb <> wbu.io.reg

  d.io.reg_in <> reg_file.io.out
  d.io.pc_io <> inst_fetch.io.pc_io
  d.io.inst := inst_fetch.io.inst

  lsu.io.exe <> d.io.exe_lsu  
  lsu.io.ctl <> c.io.ctl_lsu
  lsu.io.dmem <> io.dmem
  
  
  wbu.io.ctl <> c.io.ctl_wb
  wbu.io.exe <> d.io.exe_wbu
  wbu.io.lsu <> lsu.io.wb
  wbu.io.stall := lsu.io.stall

  // io.halt :=  d.io.ebreak would lead to conflicts in same cycle
  io.halt := Mux(d.io.ebreak, true.B, false.B)

  // object StageConnect {
  //   def apply[T <: Data](left: DecoupledIO[T], right: DecoupledIO[T]): Unit = {
  //     val arch = "single"
      
  //     if (arch == "single")         { left.bits := right.bits}
  //     else if (arch == "multi")     { right <> left}
  //     else if {arch == "pipeline"}  { right <> RegEnable(left, left.io.stall) }
     
  //     right.ready := left.ready
  //   }
  // }
}


