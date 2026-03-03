
package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.common.UtilMethods._
import npc.common.Constants._

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

class ICacheImpl(implicit val conf: Config) extends ICacheModule { 
    // val io = IO(new ICacheImplIO)
    io := DontCare

    val metas  = Module(new SRAMTemplate(new MetaBundle, nLines, nWays))
    val datas = Module(new SRAMTemplate(new DataBundle, nLines, nWays))
    
    val loadPipe = Module(new LoadPipe)
    val missUnit = Module(new MissUnit)
    val refillPipe = Module(new RefillPipe)
    val replacer = new RandomReplacement(nWays,nLines)

    loadPipe.io.req <> io.req
    loadPipe.io.dataRead        <> datas.io.r
    loadPipe.io.metaRead        <> metas.io.r
    loadPipe.io.missBus         <> missUnit.io.bus
    loadPipe.io.stall           := io.stop
    loadPipe.io.replace_way.way := replacer.way

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
  val s2_bpu_resp = RegEnable(s1_req.bpu_resp, s1_fire)
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
  val need_miss_req = s1_fire && s1_miss && !s1_fix_miss && !io.stall
  
  when(s2_miss_state === miss_idle){
    when(need_miss_req) { s2_miss_state := miss_req }
  } .elsewhen(s2_miss_state === miss_req){
    when(io.missBus.req.fire) {s2_miss_state := wait_miss_resp}
  } .elsewhen(s2_miss_state === wait_miss_resp){
    when(io.missBus.resp.valid && need_miss_req ) 
    { s2_miss_state := miss_req }
    .elsewhen(io.missBus.resp.valid){ s2_miss_state := miss_idle }
  }

  // s2_state is not in miss processing (waiting resp or has confirm request)
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
  // NOTE: icahce req should be 2 words as a line
  // val resp_data_out = resp_data.asTypeOf(Vec(blockRows,UInt(rowBits.W)))(getWordIdx(s2_addr))
  val resp_data_out = resp_data.asTypeOf(Vec(blockRows,UInt(rowBits.W)))
  val bubbles = WireDefault(VecInit(Seq.fill(blockRows)(BUBBLE)))
  // val hold_resp_data = ResultHoldBypass(resp_data_out,s2_valid_out)
    
  io.resp.valid := s2_fire
  io.resp.bits.data     := Mux(io.stall, bubbles ,resp_data_out)
  io.resp.bits.miss     := s2_miss
  io.resp.bits.pc       := s2_addr
  io.resp.bits.bpu_resp := s2_bpu_resp
  

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

class FakeICache(implicit val conf: Config) extends ICacheModule { 

    // val io = IO(new ICacheImplIO)
    io := DontCare
    val missUnit = Module(new MissUnit)
    val miss_idle :: wait_miss_resp :: should_stop :: Nil = Enum(3)
    val state = RegInit(miss_idle)
    val should_in_miss = RegInit(false.B)
    when(state === miss_idle){
        when(missUnit.io.bus.req.fire) { 
          state := wait_miss_resp 
          should_in_miss := true.B
        }
    } .elsewhen(state === wait_miss_resp){
        when(missUnit.io.bus.req.fire) { 
          state := miss_idle 
          should_in_miss := false.B
        }.elsewhen(io.stop) {
          state := should_stop
        }
    } .elsewhen(state === should_stop){
      when(missUnit.io.bus.resp.valid) { 
        state := miss_idle 
      }
    }

    when(io.stop){
      should_in_miss := false.B
    }

    missUnit.io.refill_req                := DontCare
    missUnit.io.debug                     := DontCare
    missUnit.io.bus.stall                 := io.stop
    missUnit.io.bus.req.bits.store_data   := VecInit(Seq.fill(blockRows)(0.U))
    missUnit.io.bus.req.bits.store_wmask  := VecInit(Seq.fill(blockRows)(0.U))
    missUnit.io.bus.req.bits.burst        := false.B
    missUnit.io.bus.req.bits.waymask      := 0.U
    missUnit.io.bus.req.valid             := io.req.valid
    missUnit.io.bus.req.bits.addr         := io.req.bits.addr
    missUnit.io.bus.req.bits.vaddr        := io.req.bits.addr
    missUnit.io.bus.req.bits.store        := false.B
    missUnit.io.bus.resp.ready            := io.resp.ready
    missUnit.io.axi_bus                   <> io.axi_bus
    
    val resp = missUnit.io.bus.resp
    val miss_data = resp.bits.data
    val resp_addr = ResultHoldBypass(missUnit.io.bus.req.bits.addr,missUnit.io.bus.req.fire)
    val data      = miss_data.asTypeOf(Vec(blockRows,UInt(rowBits.W)))
    val bubbles =  VecInit(Seq.fill(blockRows)(BUBBLE))
    io.req.ready            := state === miss_idle && missUnit.io.bus.req.ready
    io.resp.valid           := resp.valid && should_in_miss
    io.resp.bits.data       := Mux(io.stop,bubbles,data)
    io.resp.bits.miss       := resp.bits.miss
    io.resp.bits.pc         := resp_addr
    io.resp.bits.exception  := resp.bits.resp
    ///// DEBUG PORT
}


// object ICache {
//   def apply(req_valid: Bool,req_addr:UInt,resp_ready: Bool, fencei: Bool, stop: Bool, axi_bus: AXI4Bus, debug: ICacheDebugPort   ) (implicit conf: Config) = {
//     val icache = if(conf.HasICache) Module(new ICacheImpl) else Module(new FakeICache)
//     cache.io := DontCare
//     cache.io.req.valid      := req_valid
//     cache.io.req.bits.addr  := req_addr
//     cache.io.resp.ready     := resp_ready
//     cache.io.fencei         := fencei
//     cache.io.stop           := stop
//     cache.io.axi_bus        <> axi_bus
//     cache.io.debug          <> debug  
    
//     icache.io.resp
//   }
// }


object ICache {
  def apply() (implicit conf: Config):ICacheModule = {
    val icache = if(conf.HasICache) Module(new ICacheImpl) else Module(new FakeICache)
    icache
  }
}