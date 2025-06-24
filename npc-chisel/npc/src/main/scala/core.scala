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
  c.io.ctl  <> d.io.ctl
  c.io.dat  <> d.io.dat
  
  io.imem <> c.io.imem
  io.imem <> d.io.imem
  
  io.dmem <> c.io.dmem
  io.dmem <> d.io.dmem
  io.dmem.req.valid := c.io.dmem.req.valid
  io.dmem.req.bits.typ := c.io.dmem.req.bits.typ
  io.dmem.req.bits.fcn := c.io.dmem.req.bits.fcn
  io.halt := d.io.ebreak 
}


