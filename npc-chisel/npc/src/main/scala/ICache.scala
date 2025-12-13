
package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.common.UtilMethods._
import npc.Constants._

class ICacheDebugPort(implicit val conf: Config) extends CacheBundle { 
    val hit_cnt = Output(UInt(conf.perfCountBits.W))
    val miss_cnt = Output(UInt(conf.perfCountBits.W))
    // val load_debug = new Bundle {
    //     val s0_valid = Output(Bool())
    //     val s1_valid = Output(Bool())
    //     val s1_fire = Output(Bool())
    //     val s2_valid = Output(Bool())
    //     val s1_hit_vec = Output(Vec(nWays,Bool()))
    //     val s1_meta = Output(Vec(nWays,new MetaBundle))
    //     val s1_tag_match_way = Output(Vec(nWays,Bool()))
    //     val s2_fetch_finish = Output(Bool())
    // }
    // val missIO = new Bundle {
    //     val req_valid = Output(Bool())
    //     val req_ready = Output(Bool())
    //     val resp_valid = Output(Bool())
    //     val resp_ready = Output(Bool())
    // }

}

class ICacheIO(implicit val conf: Config) extends Bundle {
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
    io.port := DontCare
    io.port.req.bits.burst := WireDefault(BURST_FIXED)

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
    
    val ren                 = RegInit(false.B)
    val offset              = RegInit(0.U(b_bits.W)) // 当前加载偏移


    val cache_valid         = RegInit(false.B)
    val valids              = RegInit(VecInit(Seq.fill(size)(false.B))).suggestName("icache_valids") 
    val mem                 = SyncReadMem(size, UInt(cache_data_width.W))
    val metas                = SyncReadMem(size, UInt(tag_bits.W))
    val cache_block         = mem.read(io.pc(s_bits+b_bits+2-1,b_bits+2), ren || io.req_valid)
    val tag                 = metas.read(io.pc(s_bits+b_bits+2-1,b_bits+2),ren || io.req_valid)
    val cache_block_vec     = VecInit.tabulate(subBlocksPerLine) { i =>cache_block((i + 1) * conf.xlen - 1, i * conf.xlen) }
    val cacheLineBuffer     = Reg(Vec(subBlocksPerLine, UInt(conf.xlen.W))) // 块缓冲区
    val fullCacheLine       = cacheLineBuffer.asUInt
    val group_index         = if (b_bits >0) io.pc(b_bits+2-1,2) else 0.U
    val cache_data          = cache_block_vec(group_index)



    //////// pipeline icache
    // cache_valid :=  Mux( ren || io.req_valid, valids(io.pc(s_bits+b_bits+2-1,b_bits+2)),false.B)
    // tag         :=  Mux(ren || io.req_valid, metas(io.pc(s_bits+b_bits+2-1,b_bits+2)),0.U)
    // cache_data  := cache_block_vec(group_index)
    val hit             = cache_valid && (io.pc(conf.xprlen-1,s_bits+b_bits+2) === tag)
    val req_valid_reg   = RegNext(io.req_valid,true.B)
    cache_valid        := Mux( ren || io.req_valid, valids(io.pc(s_bits+b_bits+2-1,b_bits+2)),false.B)

    //////// pipeline icache
    

        // 状态迁移
    when(state === sIdle) {
        ren := false.B
        io.port.req.valid := false.B
        when(!hit && req_valid_reg) {
            state := Mux(conf.ICacheEnableBurst,sBurstRequesting,sRequesting)
            offset := 0.U
    }}
    .elsewhen(state === sRequesting ){
        when(io.port.req.ready) {
            io.port.req.valid       := state === sRequesting
            io.port.req.bits.addr   := Cat(io.pc(conf.xprlen-1,b_bits+2),offset,0.U(2.W))
            io.port.req.bits.fcn    := M_XRD
            io.port.req.bits.typ    := MT_WU 
            io.port.req.bits.burstlen := 0.U
            state := sReceiving
        }}
    .elsewhen(state === sBurstRequesting) { 
        when(io.port.req.ready) {
            io.port.req.valid           := state === sBurstRequesting
            io.port.req.bits.addr       := Cat(io.pc(conf.xprlen-1,b_bits+2),0.U(b_bits.W),0.U(2.W))
            io.port.req.bits.fcn        := M_XRD
            io.port.req.bits.typ        := MT_WU
            io.port.req.bits.burst      := BURST_INCR
            io.port.req.bits.burstlen   := Mux(conf.ICacheEnableBurst,conf.burstLength.U,0.U)
            state := sReceiving }}
    .elsewhen(state === sReceiving) {
        io.port.req.valid := false.B
        when(io.port.resp.valid) {
            offset := offset + 1.U
            cacheLineBuffer(offset) := io.port.resp.bits.data // 存储子块
            // 检查是否完成
            when(offset === (subBlocksPerLine-1).U) {
                state := sComplete
            }.elsewhen(conf.ICacheEnableBurst) { 
                state := sReceiving 
            }.otherwise {
                state := sRequesting // 继续请求下一子块
            }
        }
    }.elsewhen(state === sComplete) { 
        val index = io.pc(s_bits + b_bits + 2 - 1, b_bits+2)
        mem.write(index    , fullCacheLine)                              // 写入数据
        metas.write(index   , io.pc(conf.xprlen-1, s_bits + b_bits + 2)) // 写入Tag
        valids(index)   := true.B // 标记有效
        state   := sIdle
        ren     := true.B
    }
    
    io.inst         := Mux(hit,cache_data,BUBBLE)
    io.valid        := Mux(hit,true.B,false.B)
    io.exception    := Mux(io.port.resp.bits.resp =/= 0.U,EXC_INSTR_ACCESS_FAULT,
                        Mux(io.pc(1,0) =/= 0.U,EXC_INSTR_ADDR_MISALIGNED,EXC_NORMAL))

    when (io.fencei){
        for (addr <- 0 until size) {
            valids(addr.U):= false.B
        }
    }

    /////// DEBUG PORT
    // val hit_cnt = RegInit(0.U(conf.perfCountBits.W))
    // val miss_cnt = RegInit(0.U(conf.perfCountBits.W))
    // hit_cnt := Mux(hit && req_valid_reg,hit_cnt+1.U,hit_cnt)
    // miss_cnt := Mux(!hit && req_valid_reg,miss_cnt+1.U,miss_cnt)
    // io.debug.hit_cnt := hit_cnt
    // io.debug.miss_cnt := miss_cnt
    ////// END DEBUG
}

class ICacheReq (implicit val conf: Config) extends CacheBundle {
  val addr = UInt(conf.xprlen.W)
}

class ICacheResp (implicit val conf: Config) extends CacheBundle {
  val inst = UInt(conf.xlen.W)
  val pc   = UInt(conf.xprlen.W)
}


class ICacheImplIO(implicit val conf: Config) extends CacheBundle {
  val req           = Flipped(Decoupled(new L1Req))
  val resp          = Decoupled(new L1Resp)
  val fencei        = Input(Bool())
  val stop          = Input(Bool())
  val axi_bus       = new AXI4Bus()
  val debug         = Output(new ICacheDebugPort)
  val exception     = Output(UInt(5.W))

}

class ICacheImpl(implicit val conf: Config) extends CacheModule { 
    val io = IO(new ICacheImplIO)
    io := DontCare

    val metas  = Module(new CacheSRAMTemplate(new MetaBundle, nLines, nWays))
    val datas = Module(new CacheSRAMTemplate(new DataBundle, nLines, nWays))
    
    val loadPipe = Module(new LoadPipe)
    val missUnit = Module(new MissUnit)
    val refillPipe = Module(new RefillPipe)
    val replacer = new RandomReplacement(nWays,nLines)

    loadPipe.io.req <> io.req
    loadPipe.io.dataRead        <> datas.io.r
    loadPipe.io.metaRead        <> metas.io.r
    loadPipe.io.missBus         <> missUnit.io.bus
    loadPipe.io.stall           := io.stop
    loadPipe.io.replace_way.way := replacer.way(loadPipe.io.replace_way.idx.bits)

    missUnit.io.axi_bus <> io.axi_bus
    missUnit.io.refill_req <> refillPipe.io.req
    
    refillPipe.io.metaWrite <> metas.io.w
    refillPipe.io.dataWrite <> datas.io.w

    io.resp <> loadPipe.io.resp

    ///// DEBUG PORT
    val hit_cnt = RegInit(0.U(conf.perfCountBits.W))
    val miss_cnt = RegInit(0.U(conf.perfCountBits.W))
    val hit = (loadPipe.io.debug.s2_hit || loadPipe.io.debug.s2_fix_miss) 
    hit_cnt := Mux(hit && loadPipe.io.resp.valid   ,hit_cnt+1.U,hit_cnt)
    miss_cnt := Mux(!hit && loadPipe.io.resp.valid ,miss_cnt+1.U,miss_cnt)
    io.debug.hit_cnt := hit_cnt
    io.debug.miss_cnt := miss_cnt
    // io.debug.load_debug <> loadPipe.io.debug
    //// END DEBUG
}


class LoadPipe (implicit val conf: Config) extends CacheModule  {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new L1Req))
    val resp = Decoupled(new L1Resp)
    val metaRead = Flipped(new SRAMReadBus(new MetaBundle, nLines,nWays))
    val dataRead  = Flipped(new SRAMReadBus(new DataBundle, nLines,nWays))
    val missBus   = Flipped(new MissUnitBus)
    val replace_way = new ReplaceWayReq
    val stall     = Input(Bool())
    val debug = new Bundle{
      val s0_valid = Output(Bool())
      val s1_valid = Output(Bool())
      val s1_fire = Output(Bool())
      val s2_valid = Output(Bool())
      val s1_hit_vec = Output(Vec(nWays,Bool()))
      val s1_meta = Output(Vec(nWays,new MetaBundle))
      val s1_tag_match_way = Output(Vec(nWays,Bool()))
      val s2_fetch_finish = Output(Bool())
      val s2_hit = Output(Bool())
      val s2_fix_miss = Output(Bool())
    }
  })
  io.resp := DontCare
  io.metaRead.resp.ready := true.B
  io.dataRead.resp.ready := true.B
  io.missBus := DontCare

  // stage 0 ctrl 
  val s1_ready = Wire(Bool())
  val s0_req = io.req.bits
  val s0_valid = io.req.fire
  val s0_fire = s0_valid && s1_ready
  io.req.ready := io.metaRead.req.ready && s1_ready
  dontTouch(io.metaRead)
  // stage 0 pipeline
  val s0_addr = io.req.bits.addr
  // io.metaRead.req.valid := s0_valid
  // io.metaRead.req.bits.index := (getIdx(s0_addr))
  io.metaRead.apply(valid = s0_fire, index = getIdx(s0_addr))
  io.dataRead.apply(valid = s0_fire, index = getIdx(s0_addr))
  
  // stage 1 ctrl 
  val s2_ready    = Wire(Bool())
  val s2_miss     = Wire(Bool())
  val s2_addr_dup = Wire(UInt(conf.xprlen.W))
  val s1_valid    = RegInit(false.B)
  val s1_fire     = s1_valid && s2_ready
  s1_ready        := !s1_valid || s1_fire

  // stage 1 pipeline 
  val s1_req = RegEnable(s0_req, s0_fire)
  val s1_addr = RegEnable(s0_addr, 0.U ,s0_fire)
  val s1_meta  = ResultHoldBypass(io.metaRead.resp.bits.data,io.metaRead.resp.valid)
  val s1_datas = ResultHoldBypass(io.dataRead.resp.bits.data, io.dataRead.resp.valid)

  val s1_hit_vec = WireInit(VecInit(Seq.fill(nWays)(false.B)))
  val s1_tag_match_way = WireInit(VecInit(Seq.fill(nWays)(false.B)))
  val s1_idx = getIdx(s1_addr)
  for (i <- 0 until nWays) {
    s1_tag_match_way(i) := (s1_meta(i).tag === s1_addr.asTypeOf(addrBundle).tag)
    s1_hit_vec(i) := s1_meta(i).valid && (s1_meta(i).tag === s1_addr.asTypeOf(addrBundle).tag)
  }  
  val s1_hit = s1_hit_vec.asUInt.orR && s1_valid
  val s1_miss = !s1_hit && s1_valid
  val s1_tag_match = s1_tag_match_way.asUInt.orR


  // forward stage 1 miss data
  val s1_fix_miss       = s2_miss && getTag(s1_addr) === getTag(s2_addr_dup) && getIdx(s1_addr) === getIdx(s2_addr_dup)
  val s1_fix_resp_data  = ResultHoldBypass(io.missBus.resp.bits.data,io.missBus.resp.valid)

  // init replacement 
  io.replace_way.idx.valid := RegNext(s0_fire) 
  io.replace_way.idx.bits := s1_idx
  val s1_repl_way_en = UIntToOH(io.replace_way.way)
  val s1_need_replacement = !s1_tag_match && !s1_fix_miss 
  val s1_waymask = Mux(s1_need_replacement, s1_repl_way_en, s1_tag_match_way.asUInt)

  when(io.stall) { s1_valid := false.B }
  .elsewhen (s0_fire){ s1_valid := true.B } 
  .elsewhen (s1_fire){ s1_valid := false.B }

  // stage 2
  val s2_fetch_finish = Wire(Bool())
  val s2_not_in_miss = Wire(Bool())
  val s2_valid = RegInit(false.B)
  val s2_hit = RegEnable(s1_hit, false.B, s1_fire)
  val s2_valid_out = !io.stall && s2_valid && (s2_hit ||  s2_fetch_finish)
  val s2_fire = io.resp.ready && s2_valid_out && s2_not_in_miss
  val s2_tag_match_way = RegEnable(s1_tag_match_way,s1_fire)
  val s2_addr = RegEnable(s1_addr, 0.U, s1_fire)
  val s2_hit_vec = RegEnable(s1_hit_vec,s1_fire)
  val s2_idx = RegEnable(s1_idx, s1_fire)
  val s2_waymask = RegEnable(s1_waymask,s1_fire)
  val s2_resp_datas =  RegEnable(s1_datas,s1_fire)
  val s2_hit_resp_data =  Mux1H(s2_hit_vec, s2_resp_datas).data.asTypeOf(Vec(blockRows,UInt(rowBits.W)))
  s2_miss     := RegEnable(s1_miss, s1_fire)
  s2_addr_dup := s2_addr
  s2_ready    := (!s2_valid && s2_not_in_miss && !io.stall) || s2_fire
  // s2_ready := true.B
  
  when(io.stall) { s2_valid := false.B }
  .elsewhen (s1_fire) { s2_valid := !io.stall }
  .elsewhen(io.resp.fire) { s2_valid := false.B }
  
  // stage 2 miss 
  val miss_idle :: miss_req :: wait_miss_resp :: Nil = Enum(3)
  val s2_miss_state = RegInit(miss_idle)
  val s2_fix_miss   = RegEnable(s1_fix_miss, s1_fire)
  val s2_fix_resp_data  = RegEnable(io.missBus.resp.bits.data,s1_fire)
  val need_miss_req = s1_fire && s1_miss && !s1_fix_miss
  
  when(s2_miss_state === miss_idle){
    when(need_miss_req) { s2_miss_state := miss_req }
  } .elsewhen(s2_miss_state === miss_req){
    when(io.missBus.req.fire) {s2_miss_state := wait_miss_resp}
  } .elsewhen(s2_miss_state === wait_miss_resp){
    when(io.missBus.resp.valid && need_miss_req ) 
    { s2_miss_state := miss_req }
    .elsewhen(io.missBus.resp.valid){ s2_miss_state := miss_idle }
  }

  s2_not_in_miss := s2_miss_state === miss_idle || io.missBus.resp.valid
  s2_fetch_finish := (io.missBus.resp.valid && s2_miss) || (s2_fix_miss && s2_miss)
  io.missBus.req.valid := s2_miss_state === miss_req
  io.missBus.req.bits.addr := s2_addr
  io.missBus.req.bits.waymask := s2_waymask
  io.missBus.stall := io.stall
  io.missBus.resp.ready := io.resp.ready

  // return 
  val miss_resp_data = io.missBus.resp.bits.data
  val resp_data = Mux(s2_hit, s2_hit_resp_data, Mux(s2_fix_miss, s2_fix_resp_data, miss_resp_data))
  val resp_data_out = resp_data.asTypeOf(Vec(blockRows,UInt(rowBits.W)))(getWordIdx(s2_addr))
  // val hold_resp_data = ResultHoldBypass(resp_data_out,s2_valid_out)
  io.resp.valid := s2_fire
  io.resp.bits.data := Mux(io.stall, BUBBLE ,resp_data_out)
  io.resp.bits.miss := s2_miss
  io.resp.bits.pc   := s2_addr
  

  ////// debug
  io.debug.s1_fire := s1_fire
  io.debug.s1_valid := s1_valid
  io.debug.s0_valid := s0_valid
  io.debug.s2_valid := s2_valid
  io.debug.s1_hit_vec   := s1_hit_vec
  io.debug.s1_meta := s1_meta
  io.debug.s1_tag_match_way := s1_tag_match_way
  io.debug.s2_fetch_finish := s2_fetch_finish
  io.debug.s2_hit := s2_hit
  io.debug.s2_fix_miss := s2_fix_miss
}