
package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._


class ICacheDebugPort(implicit val conf: ysyx_24100012_Config) extends Bundle { 
    val hit_cnt = Output(UInt(conf.perfCountBits.W))
    val miss_cnt = Output(UInt(conf.perfCountBits.W))
}

class ICacheIO(implicit val conf: ysyx_24100012_Config) extends Bundle {
  val clock     = Input(Clock())
  val reset     = Input(Bool())
  val port      = new MemPortIo(conf.xlen)
  val pc        = Input(UInt(conf.xprlen.W))
  val req_valid = Input(Bool())
  val inst      = Output(UInt(conf.xlen.W))
  val valid     = Output(Bool())
  val debug     = Output(new ICacheDebugPort)
}

class ysyx_24100012_ICache(implicit val conf: ysyx_24100012_Config) extends Module { 
    val io = IO(new ICacheIO)
    io := DontCare
    io.port := DontCare

    // tag bits = 32-4-2 = 26 (16 = 2^4,4 = 2^2 bytes)
    // valid bits = 1, tag bits = 26, 32 (4bytes) 
    // 1+ 26 +32 = 59
    val mem = SyncReadMem(conf.ICacheSize,UInt(conf.xlen.W)).suggestName("ysyx_24100012_icache_mem") 
    val tags = SyncReadMem(conf.ICacheSize,UInt(26.W)).suggestName("ysyx_24100012_icache_tags") 
    val valids = SyncReadMem(conf.ICacheSize,Bool()).suggestName("ysyx_24100012_icache_valids") 
    val ren = RegInit(false.B)
    val reg_req_valid = RegNext(io.req_valid,false.B)
    
    // val in_sdram = io.pc >= SDRAM_BASE && io.pc < (SDRAM_BASE + SDRAM_SIZE)
    // val in_flash = io.pc >= FLASH_BASE && io.pc < (FLASH_BASE + FLASH_SIZE)
    // val in_psram = io.pc >= PSRAM_BASE && io.pc < (PSRAM_BASE + PSRAM_SIZE)
    // val in_mem = in_sdram || in_flash || in_psram

    // val cache_data = mem.read(io.pc(5,2),(io.req_valid || ren) && in_mem)
    // val cache_valid = cache_data(58) && in_mem
    // val hit = cache_valid && (io.pc(31,6) === cache_data(57,32))
    // io.inst := Mux(hit,cache_data(31,0),Mux(in_mem,BUBBLE,io.port.resp.bits.data))
    // io.valid := Mux(hit,true.B,Mux(in_mem,false.B,io.port.resp.valid))


    val cache_data = mem.read(io.pc(5,2),(io.req_valid || ren))
    val cache_valid = valids.read(io.pc(5,2),(io.req_valid || ren)) 
    val tag = tags.read(io.pc(5,2),(io.req_valid || ren))
    val hit = cache_valid && (io.pc(31,6) === tag)
        
    io.inst := Mux(hit,cache_data,BUBBLE)
    io.valid := Mux(hit,true.B,false.B)
    io.port.req.valid := !hit && reg_req_valid 
    io.port.req.bits.addr   := io.pc
    io.port.req.bits.fcn    := M_XRD
    io.port.req.bits.typ    := MT_WU
    
    when ( io.port.resp.valid){
        mem.write(io.pc(5,2),io.port.resp.bits.data)
        tags.write(io.pc(5,2),io.pc(31,6))
        valids.write(io.pc(5,2),true.B)
        ren := true.B
    } .otherwise {
        ren := false.B
    }


    /////// DEBUG PORT
    val hit_cnt = RegInit(0.U(conf.perfCountBits.W))
    val miss_cnt = RegInit(0.U(conf.perfCountBits.W))
    hit_cnt := Mux(hit && reg_req_valid,hit_cnt+1.U,hit_cnt)
    miss_cnt := Mux(!hit && reg_req_valid,miss_cnt+1.U,miss_cnt)
    io.debug.hit_cnt := hit_cnt
    io.debug.miss_cnt := miss_cnt
    ////// END DEBUG

}