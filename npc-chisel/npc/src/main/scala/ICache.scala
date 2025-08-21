
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


    val sIdle :: sRequesting :: sBurstRequesting :: sReceiving :: sComplete :: Nil = Enum(5)
    val state               = RegInit(sIdle)
    val s_bits              = conf.ICacheSizeBits
    val b_bits              = conf.ICacheBlockBits
    val size                = 1 << conf.ICacheSizeBits 
    val subBlocksPerLine    = 1 << b_bits
    val cache_data_width = subBlocksPerLine * conf.xlen
    val tag_bits            = conf.xlen-s_bits-b_bits-2
    // tag bits = xprlen-s_bits-b_bits-2bits(4bytes)
    //          = 32-4-1-2 = 25 (16 = 2^4,4 = 2^2 bytes)
    // valid bits = 1, tag bits = 25, b_bits = 1 
    // 1+ 25 +32 = 58
    
    val ren = RegInit(false.B)
    val offset              = RegInit(0.U(b_bits.W)) // 当前加载偏移
    val reg_req_valid       = RegNext(io.req_valid,false.B)
    val cacheLineBuffer     = Reg(Vec(subBlocksPerLine, UInt(conf.xlen.W))) // 块缓冲区
    val mem = SyncReadMem(size,UInt(cache_data_width.W)).suggestName("ysyx_24100012_icache_mem") 
    val tags = SyncReadMem(size,UInt(tag_bits.W)).suggestName("ysyx_24100012_icache_tags") 
    val valids = SyncReadMem(size,Bool()).suggestName("ysyx_24100012_icache_valids") 
    
    // val in_sdram = io.pc >= SDRAM_BASE && io.pc < (SDRAM_BASE + SDRAM_SIZE)
    // val in_flash = io.pc >= FLASH_BASE && io.pc < (FLASH_BASE + FLASH_SIZE)
    // val in_psram = io.pc >= PSRAM_BASE && io.pc < (PSRAM_BASE + PSRAM_SIZE)
    // val in_mem = in_sdram || in_flash || in_psram

    // val cache_data = mem.read(io.pc(5,2),(io.req_valid || ren) && in_mem)
    // val cache_valid = cache_data(58) && in_mem
    // val hit = cache_valid && (io.pc(31,6) === cache_data(57,32))
    // io.inst := Mux(hit,cache_data(31,0),Mux(in_mem,BUBBLE,io.port.resp.bits.data))
    // io.valid := Mux(hit,true.B,Mux(in_mem,false.B,io.port.resp.valid))

    val group_index = io.pc(b_bits+2-1,2)
    val cache_block = mem.read(io.pc(s_bits+b_bits+2-1,b_bits+2),(io.req_valid || ren))
    val cache_block_vec =  VecInit.tabulate(subBlocksPerLine) { i =>cache_block((i + 1) * conf.xlen - 1, i * conf.xlen) }
    val cache_data = cache_block_vec(group_index)
    val cache_valid = valids.read(io.pc(s_bits+b_bits+2-1,b_bits+2),(io.req_valid || ren))
    val tag = tags.read(io.pc(s_bits+b_bits+2-1,b_bits+2),(io.req_valid || ren))
    val hit = cache_valid && (io.pc(conf.xprlen-1,s_bits+b_bits+2) === tag)
        
    io.inst := Mux(hit,cache_data,BUBBLE)
    io.valid := Mux(hit,true.B,false.B)
    when (state === sRequesting){
        io.port.req             := DontCare
        io.port.req.valid       := state === sRequesting
        io.port.req.bits.addr   := Cat(io.pc(conf.xprlen-1,b_bits+2),offset,0.U(2.W))
        io.port.req.bits.fcn    := M_XRD
        io.port.req.bits.typ    := MT_WU
    }
    when(state === sBurstRequesting) {
        io.port.req.valid           := state === sBurstRequesting
        io.port.req.bits.addr       := Cat(io.pc(conf.xprlen-1,b_bits+2),0.U(b_bits.W),0.U(2.W))
        io.port.req.bits.fcn        := M_XRD
        io.port.req.bits.typ        := MT_WU
        io.port.req.bits.burst      := true.B
        io.port.req.bits.burstlen   := conf.burstLength
    }
    

        // 状态迁移
    switch(state) {
        is(sIdle) {
            ren := false.B
            when(!hit && reg_req_valid) {
                state := Mux(conf.ICacheEnableBurst,sRequesting,sBurstRequesting)
                offset := 0.U }}
        is(sRequesting) { state := sReceiving }
        is(sReceiving) {
            when(io.port.resp.valid) {
                cacheLineBuffer(offset) := io.port.resp.bits.data // 存储子块
                offset := offset + 1.U
                // 检查是否完成
                when(offset === (subBlocksPerLine-1).U) {
                    state := sComplete
                }.elsewhen(conf.ICacheEnableBurst) { 
                    state := sReceiving 
                }.otherwise {
                    state := sRequesting // 继续请求下一子块
                }
            }
        }
        is(sComplete) { 
            state   := sIdle
            ren     := true.B
        }
    }

    
    val fullCacheLine = cacheLineBuffer.asUInt

    // 写入缓存（仅当完成整行加载）
    when(state === sComplete) {
        val index = io.pc(s_bits + b_bits + 2 - 1, b_bits+2)
        mem.write(index, fullCacheLine) // 写入数据
        tags.write(index, io.pc(conf.xprlen-1, s_bits + b_bits + 2)) // 写入Tag
        valids.write(index, true.B) // 标记有效
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