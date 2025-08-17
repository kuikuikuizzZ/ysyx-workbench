
package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._

class ICacheIO(implicit val conf: ysyx_24100012_Config) extends Bundle {
  val clock     = Input(Clock())
  val reset     = Input(Bool())
  val port      = Flipped(new MemPortIo(conf.xlen))
  val pc        = Input(UInt(conf.xprlen.W))
  val req_valid = Input(Bool())
  val inst      = Output(UInt(conf.xlen.W))
  val valid     = Output(Bool())
}

class ysyx_24100012_ICache(implicit val conf: ysyx_24100012_Config) extends Module { 
    val io = IO(new ICacheIO)
    // tag bits = 32-4-2 = 26 (16 = 2^4,4 = 2^2 bytes)
    // valid bits = 1, tag bits = 26, 32 (4bytes) 
    // 1+ 26 +32 = 59
    val mem = SyncReadMem(conf.ICacheSize,UInt(conf.ICacheBlockBits.W))

    val cache_data = mem.read(io.pc(5,2),io.req_valid).asUInt
    val cache_valid = cache_data(58)
    val hit = cache_valid && (io.pc(31,6) === cache_data(57,32))
    
    io.port.req.valid := !hit && io.req_valid
    io.port.req.bits.addr := io.pc
    io.port.req.bits.fcn := M_XRD
    io.port.req.bits.typ := MT_WU
    when (io.port.resp.valid){
        mem.write(io.pc(5,2),Cat(1.U,io.pc(31,6),io.port.resp.bits.data))
    }
    
    io.inst := Mux(hit,cache_data(31,0),0.U)
    io.valid := Mux(hit,true.B,false.B)

}