package npc.devices

import chisel3._
import chisel3.util._

import npc.common.Constants._
import npc.common._

class YSYX2400012AXI4LiteMem(implicit val conf: YSYX24100012Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012AXI4LiteMem.v"
   addPath(path)
   println(s"YSYX2400012AsyncMem path: ${path}")
}

class YSYX2400012AXI4LiteMemRandomDelay(implicit val conf: YSYX24100012Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012AXI4LiteMemRandomDelay.v"
   addPath(path)
   println(s"YSYX2400012AXI4LiteMemRandomDelay path: ${path}")
}
