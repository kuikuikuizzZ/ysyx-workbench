package npc.devices

import chisel3._
import chisel3.util._

import npc.common.Constants._
import npc.common._

class AXI4LiteMem(implicit val conf: Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/AXI4LiteMem.v"
   addPath(path)
   println(s"AsyncMem path: ${path}")
}

class AXI4LiteMemRandomDelay(implicit val conf: Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/AXI4LiteMemRandomDelay.v"
   addPath(path)
   println(s"AXI4LiteMemRandomDelay path: ${path}")
}

class AsyncMem(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dr = new AXIRport(addrWidth,32)
      val dw = new AXIWport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/AsyncMem.v"
   addPath(path)
   println(s"AsyncMem path: ${path}")
}
