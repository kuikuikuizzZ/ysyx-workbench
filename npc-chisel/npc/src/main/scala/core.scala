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
  val c  = Module(new YSYX24100012Cpath())
  val d  = Module(new YSYX24100012Dpath())
  val inst_fetch = Module(new YSYX24100012InstFetch())
  val reg_file = Module(new RegFile())

  c.io.ctl  <> d.io.ctl
  c.io.dat  <> d.io.dat
  c.io.inst := inst_fetch.io.inst

  reg_file.io.inst := inst_fetch.io.inst
  reg_file.io.wb_data := d.io.wb_data
  reg_file.io.rf_wen := c.io.rf_wen

  d.io.reg_in <> reg_file.io.reg_to_dat_io
  d.io.pc_io <> inst_fetch.io.pc_io
  d.io.inst := inst_fetch.io.inst
  inst_fetch.io.targets <> d.io.targets
  inst_fetch.io.pc_sel :=  c.io.pc_sel
  
  io.imem <> inst_fetch.io.imem
  
  io.dmem <> c.io.dmem
  io.dmem <> d.io.dmem
  io.dmem.req.valid := c.io.dmem.req.valid
  io.dmem.req.bits.typ := c.io.dmem.req.bits.typ
  io.dmem.req.bits.fcn := c.io.dmem.req.bits.fcn
  // io.halt :=  d.io.ebreak would lead to conflicts in same cycle
  io.halt := Mux(d.io.ebreak, true.B, false.B)
}


