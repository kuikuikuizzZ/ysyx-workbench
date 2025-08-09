
package npc.devices

import chisel3._
import chisel3.util._

import npc.common.Constants._
import npc.common._

object  CLINTS{
    val mtime = 0XBFF8
    val mtime_high = 0XBFFC
}

class ysyx_24100012_AXI4CLINT(implicit val conf: ysyx_24100012_Config) extends Module { 
    val io = IO(new Bundle() { 
        val axi_io = new AXI4LiteIO(conf.xprlen)
        val reset = Input(Bool())
        val clock = Input(Clock())
    })

    val clint = Module(new ysyx_24100012_CLINT())
    val node = Module(new ysyx_24100012_AXI4LiteSlave())
    node.io.axi_io <> io.axi_io
    node.io.reset := io.reset
    node.io.clock := io.clock
    node.io.out <> clint.io.in 

}

class ysyx_24100012_CLINT(implicit val conf: ysyx_24100012_Config) extends Module { 
    val io = IO(new Bundle() { 
        val reset = Input(Bool())
        val clock = Input(Clock())
        val in =new Bundle{
            val dr      =   new AXIRport(conf.xprlen, conf.xlen)
            val dw      =   new AXIWport(conf.xprlen, conf.xlen)
        }
    })
    val reg_mtime = RegInit(0.U(64.W))
    val read_mapping = collection.mutable.LinkedHashMap[Int,Bits](
        CLINTS.mtime -> reg_mtime(31,0),
        CLINTS.mtime_high -> reg_mtime(63,32)
    )
    val decoded_addr = read_mapping map { case (k, v) => k -> (io.in.dr.addr === k) }
    
    io.in.dw := DontCare
    io.in.dr.ready := true.B
    io.in.dr.data := Mux1H(for ((k, v) <- read_mapping) yield decoded_addr(k) -> v)
    
    reg_mtime := reg_mtime + 1.U
}