package npc.devices

import chisel3._
import chisel3.util._

import npc.common.Constants._
import npc.common._

class ysyx_24100012_AXI4LiteMem(implicit val conf: ysyx_24100012_Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/ysyx_24100012_AXI4LiteMem.v"
   addPath(path)
   println(s"ysyx_24100012_AsyncMem path: ${path}")
}

class ysyx_24100012_AXI4LiteMemRandomDelay(implicit val conf: ysyx_24100012_Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/ysyx_24100012_AXI4LiteMemRandomDelay.v"
   addPath(path)
   println(s"ysyx_24100012_AXI4LiteMemRandomDelay path: ${path}")
}

class ysyx_24100012_AsyncMem(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dr = new AXIRport(addrWidth,32)
      val dw = new AXIWport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/ysyx_24100012_AsyncMem.v"
   addPath(path)
   println(s"ysyx_24100012_AsyncMem path: ${path}")
}
