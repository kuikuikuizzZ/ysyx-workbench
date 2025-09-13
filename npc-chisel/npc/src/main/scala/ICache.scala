
package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._

class ICacheDebugPort(implicit val conf: Config) extends Bundle { 
    val hit_cnt = Output(UInt(conf.perfCountBits.W))
    val miss_cnt = Output(UInt(conf.perfCountBits.W))
}

class ICacheIO(implicit val conf: Config) extends Bundle {
  val reset     = Input(Bool())
  val pc        = Input(UInt(conf.xprlen.W))
  val fencei    = Input(Bool())
  val req_valid = Input(Bool())
  val inst      = Output(UInt(conf.xlen.W))
  val valid     = Output(Bool())
  val exception = Output(UInt(5.W))
  val debug     = Output(new ICacheDebugPort)
  val port      = new MemPortIo(conf.xlen)
  
}

class ICache(implicit val conf: Config) extends Module { 
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
    val mem = SyncReadMem(size,UInt(cache_data_width.W)).suggestName("icache_mem") 
    val tags = SyncReadMem(size,UInt(tag_bits.W)).suggestName("icache_tags") 
    val valids = SyncReadMem(size,Bool()).suggestName("icache_valids") 

    val group_index = io.pc(b_bits+2-1,2)
    val cache_block = mem.read(io.pc(s_bits+b_bits+2-1,b_bits+2),(io.req_valid || ren))
    val cache_block_vec =  VecInit.tabulate(subBlocksPerLine) { i =>cache_block((i + 1) * conf.xlen - 1, i * conf.xlen) }
    val cache_data = cache_block_vec(group_index)
    val cache_valid = valids.read(io.pc(s_bits+b_bits+2-1,b_bits+2),(io.req_valid || ren))
    val tag = tags.read(io.pc(s_bits+b_bits+2-1,b_bits+2),(io.req_valid || ren))
    val hit = cache_valid && (io.pc(conf.xprlen-1,s_bits+b_bits+2) === tag)
    

    // when (io.reset) {
    // // 注意：在Chisel中，我们通常避免在复位时进行循环写操作，因为这样可能会产生非常大的硬件。
    // // 但在仿真中，我们可以使用这样的初始化。在综合时，这个循环可能会被优化掉，或者需要特定的综合支持。
    //     for (i <- 0 until size) {
    //         mem.write(i.U, 0.U(cache_data_width.W))
    //         tags.write(i.U, 0.U(tag_bits.W))
    //         valids.write(i.U, false.B)
    //     }
    // }

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
        io.port.req.bits.burst      := BURST_INCR
        io.port.req.bits.burstlen   := Mux(conf.ICacheEnableBurst,conf.burstLength,0.U)
    }

        // 状态迁移
    switch(state) {
        is(sIdle) {
            ren := false.B
            io.exception  := Mux(io.pc(1,0) =/= 0.U,EXC_INSTR_ADDR_MISALIGNED,EXC_NORMAL)
            when(!hit && reg_req_valid) {
                when (io.pc(1,0) =/= 0.U) { // 非4字节对齐，直接报异常
                    state := sIdle
                } .otherwise{
                    state := Mux(conf.ICacheEnableBurst,sBurstRequesting,sRequesting)
                    offset := 0.U
                }}}
        is(sRequesting) { when(io.port.req.ready) {state := sReceiving }}
        is(sBurstRequesting) { when(io.port.req.ready) {state := sReceiving }}
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
                when(io.port.resp.bits.resp =/= 0.U) {
                    io.exception  := EXC_INSTR_ACCESS_FAULT
                }.otherwise {
                    io.exception  := EXC_NORMAL
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

    io.inst          := Mux(hit,cache_data,BUBBLE)
    io.valid         := Mux(hit,true.B,false.B)
    when (io.fencei){
        for (addr <- 0 until size) {
            valids.write(addr.U, false.B)
        }
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