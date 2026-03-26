
package npc

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR

import npc.common._
import npc.common.UtilMethods._
import npc.common.Constants._
import java.rmi.server.UID

sealed trait HasL1Params{
  implicit val conf: Config
  val nSets:     Int = 1
  val nWays:     Int = 4
  val nLines:    Int = 16
  val rowBits:   Int = conf.xlen
  val rowBytes:  Int = rowBits/8
  val blockBytes:Int = conf.fetchGroupBytes // no cache 
  val blockBits: Int = blockBytes*8
  val singleSet = nSets == 1
  val blockRows = blockBytes/rowBytes
  val blockRowsBits  = log2Ceil(blockRows)
  def tagBits: Int = conf.xlen - idxBits - blockRowsBits - byteOffsetBits
  def idxBits: Int = log2Ceil(nLines) 
  def addrBits: Int = conf.xprlen
  def byteOffsetBits: Int = 2
  def addrBundle = new Bundle {
    val tag = UInt(tagBits.W)
    val index = UInt(idxBits.W)
    val wordIndex = UInt(blockRowsBits.W)
    val byteOffset = UInt(2.W)
  }

  def getIdx(addr: UInt) = addr.asTypeOf(addrBundle).index
  def getTag(addr: UInt) = addr.asTypeOf(addrBundle).tag
  def getWordIdx(addr: UInt) = if (blockRowsBits != 0) addr.asTypeOf(addrBundle).wordIndex else 0.U
  def mergePutData(old_data: UInt, new_data: UInt,wmask: UInt): UInt = {
    val full_mask = FillInterleaved(8, wmask)
    (old_data & ~full_mask) | (new_data & full_mask)
  }
  def mergeBlockData(old_data: Vec[UInt], new_data: UInt, wmask: UInt): Vec[UInt] = {
    val blockRows = old_data.length
    require(wmask.getWidth >= blockRows, "wmask width must be at least old_data length")
    
    val mask_vec = VecInit(Seq.tabulate(blockRows) { i =>
      Mux(wmask(i), ~0.U(32.W), 0.U(32.W))
    })
    
    VecInit((0 until blockRows).map { i =>
      (old_data(i) & ~mask_vec(i)) | (new_data & mask_vec(i))
    })
  }
  def isMMIO(addr: UInt): Bool = {
    // 这里简单地将地址的高位作为MMIO判断条件，实际设计中可能需要更复杂的逻辑
    addr(addrBits-1, addrBits-4) === "hFFFF".U
  }
}

abstract class CacheBundle extends Bundle with HasL1Params
abstract class CacheModule extends Module with HasL1Params
abstract class CacheConfig extends HasParameters with HasL1Params
abstract class ICacheModule extends CacheModule with HasL1Params{
  val io = IO(new ICacheImplIO)
}


sealed class MetaBundle (implicit val conf: Config)extends CacheBundle {
  val tag = Output(UInt(tagBits.W))
  val valid = Output(Bool())
  val dirty = Output(Bool())
  def apply(tag: UInt,valid : Bool) = {
    this.tag := tag
    this.valid := valid 
    this
  }
}

sealed class DataBundle(implicit val conf: Config) extends CacheBundle {
  val data = Output(UInt(blockBits.W))

  def apply(data: UInt) = {
    this.data := data
    this
  }
}
object DataBundle{
  implicit val conf = new Config()

  // 可以添加工厂方法、常量等
  def apply(data: UInt): DataBundle = {
    val bundle = new DataBundle
    bundle.data := data
    bundle
  }
}





class ReplaceWayReq (implicit val conf: Config) extends CacheBundle {
  val idx = ValidIO(UInt(idxBits.W))
  val way = Input(UInt(log2Up(nWays).W))
}

class ReplaceAccess (implicit val conf: Config) extends CacheBundle {
  val idx = (UInt(idxBits.W))
  val way = Input(UInt(log2Up(nWays).W))
}

class RefillReq (implicit val conf: Config)extends CacheBundle {
  val addr    = Input(UInt(conf.xprlen.W))
  val wayMask = Input(UInt(nWays.W))
  val data    = Input(Vec(blockRows,UInt(conf.xlen.W)))
}

class RefillResp (implicit val conf: Config)extends CacheBundle {
  val data = Output(Bool())
}

class MainPipeReq (implicit val conf: Config) extends CacheBundle {
  val addr    = Input(UInt(conf.xprlen.W))
  val mask    = Input(UInt((conf.xlen/8).W))
  val wdata   = Input(UInt(conf.xlen.W))
  val miss    = Input(Bool())
  val replace = Input(Bool())
}


class RefillPipe (implicit val conf: Config) extends CacheModule {
  val io = IO(new Bundle(){
    val req = Flipped(Decoupled(new RefillReq))
    // val resp = Output(Bool())
    // val metaRead = new SRAMReadBus(new MetaBundle, nLines,nWays)
    // val dataRead = new SRAMReadBus(new DataBundle, nLines,nWays)
    val metaWrite = Flipped(new SRAMWriteBus(new MetaBundle, nLines,nWays))
    val dataWrite = Flipped(new SRAMWriteBus(new DataBundle, nLines,nWays))
  })

  io.req.ready := io.dataWrite.req.ready && io.metaWrite.req.ready
  // io.resp := io.req.fire

  val idx = getIdx(io.req.bits.addr)
  val tag = getTag(io.req.bits.addr)

  io.dataWrite.req.valid := io.req.valid
  io.dataWrite.req.bits.index := idx
  io.dataWrite.req.bits.data.data := io.req.bits.data.asUInt
  io.dataWrite.req.bits.waymask := io.req.bits.wayMask

  io.metaWrite.req.valid := io.req.valid
  io.metaWrite.req.bits.index := idx
  io.metaWrite.req.bits.data.tag := tag
  io.metaWrite.req.bits.data.valid := true.B
  io.metaWrite.req.bits.data.dirty := false.B
  io.metaWrite.req.bits.waymask := io.req.bits.wayMask
}

class MissReq (implicit val conf: Config) extends CacheBundle {
  val addr = Input(UInt(conf.xprlen.W))
  // val vaddr = Input(UInt(conf.xprlen.W))
  val waymask = Input(UInt(nWays.W)) 

  val store_data = Input(Vec(blockRows,UInt(rowBits.W)))
  val store_wmask = Input(Vec(blockRows,UInt(rowBytes.W)))
  val store = Input(Bool())
  val burst = Input(Bool())
}

class MissResp (implicit val conf: Config) extends CacheBundle {
  val data = Output(Vec(blockRows,UInt(conf.xlen.W)))
  val resp = Output(UInt(2.W)) // OKAY or SLVERR
  val addr = Output(UInt(conf.xprlen.W))
  val miss = Output(Bool())
}

class MissUnitBus(implicit val conf: Config) extends CacheBundle {
  val req = Flipped(DecoupledIO(new MissReq))
  val stall = Input(Bool())
  val resp = Decoupled(new MissResp)
} 

class MissUnit(implicit val conf: Config) extends CacheModule {
    val io = IO(new Bundle {
        val bus = new MissUnitBus
        val axi_bus = new AXI4Bus()
        // val main_pipe = Flipped(new MainPipeReq)
        val refill_req = Decoupled(new RefillReq)
        // val refill_resp = Input(Bool())
        val debug = new Bundle{
          val state = Output(UInt(2.W))
        }
    })
    // io.debug := DontCare

    io.axi_bus.req.bits := DontCare
    
    io.axi_bus.resp.ready := true.B
    val req_fire = Wire(Bool()) 
    val sRequesting :: sReceiving :: sRequestAgain :: sComplete :: Nil = Enum(4)

    val state   =   RegInit(sRequesting)
    val cacheLine = RegInit(VecInit(Seq.fill(blockRows)(0.U(conf.xlen.W))))
    val addr = ResultHoldBypass(io.bus.req.bits.addr,io.bus.req.valid)
    val offset = RegInit(0.U(blockRowsBits.W))
    val store_mask = io.bus.req.bits.store_wmask
    val store_data = io.bus.req.bits.store_data
    req_fire := io.bus.req.fire

    val refill_store_data = Wire(Vec(blockRows,UInt(rowBits.W)))
    val refill_store_mask = Wire(Vec(blockRows,UInt(rowBytes.W)))

    for(i <- 0 until blockRows) {
      refill_store_data(i) := store_data(i)
      refill_store_mask(i) := store_mask(i)
    }
    val is_requesting = state === sRequesting && (io.bus.req.valid) 
    val is_request_again = state === sRequestAgain 
    io.axi_bus.req.valid := (is_requesting || is_request_again) 
    io.axi_bus.req.bits.ren           := true.B
    io.axi_bus.req.bits.wen           := false.B
    
    when (io.bus.req.bits.burst){
      io.axi_bus.req.bits.raddr       := Cat(addr(conf.xprlen-1,blockRowsBits+byteOffsetBits),0.U(blockRowsBits.W+byteOffsetBits.W))
      io.axi_bus.req.bits.burst       := BURST_INCR
      io.axi_bus.req.bits.burstlen    := blockRowsBits.U
    }.otherwise {
      io.axi_bus.req.bits.raddr       := Cat(addr(conf.xprlen-1,blockRowsBits+byteOffsetBits),offset,0.U(byteOffsetBits.W))
      io.axi_bus.req.bits.burstlen    := 0.U
      io.axi_bus.req.bits.burst       := BURST_FIXED
    }

    when(state === sRequesting ){
      when(io.bus.req.valid  && io.axi_bus.req.ready ) {
        state := sReceiving
    }} .elsewhen(state === sRequestAgain) {
      when(io.axi_bus.req.ready) {
        state := sReceiving
      }
    }.elsewhen(state === sReceiving) {
        when(io.axi_bus.resp.valid) {
          // TODO: maybe need to support other burstlen 
          offset := offset + 1.U
          when(io.bus.req.bits.store === true.B) {  // store 
            cacheLine(offset) := mergePutData(io.axi_bus.resp.bits.data, 
                                  refill_store_data(offset),
                                  refill_store_mask(offset))
          } .otherwise {
            cacheLine(offset) := io.axi_bus.resp.bits.data  // 存储子块
          }
          // 检查是否完成
          when(offset === (blockRows-1).U) {
              state := sComplete
          }.elsewhen(io.bus.req.bits.burst && !io.axi_bus.resp.bits.last ) { 
              state := sReceiving 
          }.otherwise {
              state :=  sRequestAgain // 继续请求下一子块
          }
        }
    }.elsewhen(state === sComplete) { 
        io.axi_bus.req.valid := false.B 
        when(io.bus.resp.fire){
          state   := sRequesting
          offset := 0.U
        }
    }

    val resp_data = Wire(Vec(blockRows,UInt(rowBits.W)))
    for (i <- 0 until blockRows) {
      resp_data(i) := cacheLine(i)
    }
    // wait for refill complete
    io.bus.req.ready := io.axi_bus.req.ready && (state === sRequesting)
    io.bus.resp.valid := state === sComplete 
    io.bus.resp.bits.data := resp_data
    io.bus.resp.bits.resp := 0.U // OKAY
    io.bus.resp.bits.addr := addr
    io.bus.resp.bits.miss := true.B

    io.refill_req.valid := state === sComplete 
    io.refill_req.bits.addr := addr
    io.refill_req.bits.data := resp_data
    io.refill_req.bits.wayMask := io.bus.req.bits.waymask


    /////// debug
    io.debug.state := state
}

class WriteBackReq (implicit val conf: Config) extends CacheBundle {
  val addr = Input(UInt(conf.xprlen.W))
  val data = Input(Vec(blockRows,UInt(rowBits.W)))
}

class WriteBackResp (implicit val conf: Config) extends CacheBundle {
  val addr = Output(UInt(conf.xprlen.W))
  val miss = Output(Bool())
}

class WriteBackUnit(implicit val conf: Config) extends CacheModule { 
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new WriteBackReq))
    val resp = Decoupled(new WriteBackResp)
    val axi_bus = new AXI4Bus()
  })
  val s_idle :: s_req :: s_wait_last :: Nil = Enum(3)
  val state = RegInit(s_idle)
  io.req.ready := io.axi_bus.req.ready && (state === s_idle)

  val beat_cnt = RegInit(0.U(blockRowsBits.W)) 
  switch(state){ 
      is(s_idle) {
          when( io.axi_bus.req.ready){
              state :=s_req
              beat_cnt := beat_cnt + 1.U
          }
      }
      is(s_req) {
          when(io.axi_bus.resp.valid && beat_cnt === (blockRows-1).U){
              state := s_idle
              beat_cnt := 0.U
          } .elsewhen(io.axi_bus.resp.valid){
              beat_cnt := beat_cnt + 1.U
          }
      }
  }  
  /////////// Write Port
  val addr = io.req.bits.addr
  io.axi_bus.req.valid := (state === s_idle && io.req.valid) || state === s_req
  io.axi_bus.req.bits.burst := BURST_FIXED
  io.axi_bus.req.bits.burstlen := 0.U // single transfer
  io.axi_bus.req.bits.raddr := addr
  io.axi_bus.req.bits.ren   := false.B
  
  io.axi_bus.req.bits.waddr := addr + (beat_cnt << 2.U) // write beat address
  io.axi_bus.req.bits.wen   := true.B   
  io.axi_bus.req.bits.typ   := 0.U // TODO: support different access type
  io.axi_bus.req.bits.data := io.req.bits.data(beat_cnt)
  io.axi_bus.req.bits.mask  := "b1111".U
  io.axi_bus.resp.ready     := true.B

  io.resp.valid := state === s_req && io.axi_bus.resp.valid && beat_cnt === (blockRows-1).U
  io.resp.bits.addr := addr
  io.resp.bits.miss := true.B
}


class MainMissResp (implicit val conf: Config) extends CacheBundle {
  val miss      = Output(Bool())
  val addr      = Output(UInt(conf.xprlen.W))
  val tag       = Output(UInt(tagBits.W))
  val waymask   = Output(UInt(nWays.W))
  val data      = Output(Vec(blockRows,UInt(rowBits.W)))
}

class StoreRetireReq (implicit val conf: Config) extends CacheBundle {
  val addr = Output(UInt(conf.xprlen.W))
  // val data = Output(Vec(blockRows,UInt(rowBits.W)))
  // val waymask = Output(UInt(nWays.W))
}

class MainPipe (implicit val conf: Config) extends CacheModule  {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new L1Req))
    val metaRead      = Flipped(new SRAMReadBus(new MetaBundle, nLines,nWays))
    val dataRead      = Flipped(new SRAMReadBus(new DataBundle, nLines,nWays))
    val metaWrite     = Flipped(new SRAMWriteBus(new MetaBundle, nLines,nWays))
    val dataWrite     = Flipped(new SRAMWriteBus(new DataBundle, nLines,nWays))
    val replace_way   = new ReplaceWayReq
    val missBus       = Flipped(new MissUnitBus)
    val miss_resp     = Decoupled(new MainMissResp())
    val wb_req        = Decoupled(new WriteBackReq)
    val wb_resp       = Flipped(Decoupled(new WriteBackResp))
    // val s2_resp       = Decoupled(new L1Resp)
    val retire_store  = Flipped(Decoupled(new StoreRetireReq))
    val stall         = Input(Bool())
  })
  io.metaRead.resp.ready := true.B
  io.dataRead.resp.ready := true.B
  // s0 ctrl
  val s1_ready = Wire(Bool())
  val s0_fire = io.req.valid && s1_ready

  val s0_req = io.req
  val s0_addr = io.req.bits.addr
  val s0_idx = getIdx(s0_addr)
  val full_write = io.req.bits.wmask.andR
  val store_rmask = ~io.req.bits.wmask
  val store_need_data = store_rmask.orR

  io.req.ready := io.metaRead.req.ready && s1_ready
  io.metaRead.apply(valid = s0_fire, index = s0_idx)

  // s1 ctrl
  val s2_ready = Wire(Bool()) 
  val s2_miss  = Wire(Bool())
  val s1_valid = RegNext(false.B)
  val s1_fire = s1_valid && s2_ready && io.dataWrite.req.ready
  s1_ready := !s1_valid || s2_ready


  when (s0_fire) {
    s1_valid := true.B
  }.elsewhen (s1_fire) {
    s1_valid := false.B
  }

  val s1_req        = RegEnable(s0_req.bits, s0_fire)
  val s1_store_mask = RegEnable(s0_req.bits.wmask, s0_fire)
  val s1_meta       = io.metaRead.resp.bits.data
  val s1_idx        = getIdx(s1_req.addr)
  val s1_rmask      = RegEnable(store_rmask, s0_fire)
  val s1_need_data  = RegEnable(store_need_data, s0_fire)
  val s1_addr       = RegEnable(s0_addr, 0.U ,s0_fire)  
  val s1_hit_vec = WireInit(VecInit(Seq.fill(nWays)(false.B)))
  val s1_tag_match_way = WireInit(VecInit(Seq.fill(nWays)(false.B)))
  val s1_invalid_vec = WireInit(VecInit(Seq.fill(nWays)(false.B)))
   
  for (i <- 0 until nWays) {
    s1_tag_match_way(i) := 
      (s1_meta(i).tag === s1_addr.asTypeOf(addrBundle).tag)
    s1_hit_vec(i) := s1_meta(i).valid && (s1_meta(i).tag === s1_addr.asTypeOf(addrBundle).tag)
    s1_invalid_vec(i) := !s1_meta(i).valid
  }  
  val s1_hit = s1_hit_vec.asUInt.orR && s1_valid
  val s1_miss = !s1_hit && s1_valid
  val s1_tag_match = s1_tag_match_way.asUInt.orR
  val s1_have_invalid_way = s1_invalid_vec.asUInt.orR
  val s1_invalid_way = PriorityEncoder(s1_invalid_vec)
  val selected_invalid_way = Mux(s1_have_invalid_way,s1_invalid_way , 0.U)
  
  // no hit should fill a place store if have invalid way, 
  // otherwise replace the way from replacement policy
  // init replacement 
  io.replace_way.idx.valid := RegNext(s0_fire) 
  io.replace_way.idx.bits := s1_idx
  val s1_repl_way_en = UIntToOH(io.replace_way.way)
  val s1_need_replacement = !s1_tag_match && !s1_have_invalid_way 
  val s1_waymask = Mux(s1_need_replacement, s1_repl_way_en,
    Mux(s1_have_invalid_way,selected_invalid_way, s1_hit_vec.asUInt))

  // not full write and hit should read data then merge
  io.dataRead.apply(valid =  s1_valid && ((s1_need_data && s1_hit) || s1_need_replacement), index = s1_idx)
  

  // s2 ctrl
  val s3_ready  = Wire(Bool())
  val s2_valid  = RegInit(false.B)
  val s2_hit = RegEnable(s1_hit, s1_fire)
  val s2_need_replacement = RegEnable(s1_need_replacement, s1_fire)
  val s2_can_go_miss = io.missBus.resp.valid
  val s2_can_go_s3 = s2_hit && s2_valid
  val s2_can_go = (s2_can_go_miss || s2_can_go_s3 ) && s3_ready 
  s2_ready  := !s2_valid || s2_can_go 
  s2_miss   := RegEnable(s1_miss, s1_fire)
  val s2_fire = s2_valid && s2_ready



  when (s1_fire) {
    s2_valid := true.B
  }.elsewhen (s2_fire) {
    s2_valid := false.B
  }



  val s2_req = RegEnable(s1_req, s1_fire)
  val s2_waymask = RegEnable(s1_waymask, s1_fire)
  val s2_idx = getIdx(s2_req.addr)
  val s2_addr = s2_req.addr
  val s2_mmio = isMMIO(s2_addr)
  val s2_hit_vec = RegEnable(s1_hit_vec, s1_fire)

  
  val s2_wordIdx    = getWordIdx(s2_addr)
  val s2_wordIdxOH  = UIntToOH(s2_wordIdx)
  val s2_miss_resp  = io.missBus.resp.bits.data
  val s2_old_block  = Mux1H(s2_hit_vec, io.dataRead.resp.bits.data).data.asTypeOf(Vec(blockRows,UInt(conf.xlen.W))) 
  val s2_data_resp  = Mux(s2_can_go_s3,s2_old_block,s2_miss_resp)
  val s2_old_data   = s2_old_block(s2_wordIdx) 
  val s2_merge_data = mergePutData( s2_old_data, s2_req.wdata,  s2_req.wmask)
  val s2_wbdata     = s2_old_block
  val s2_need_wb    = s2_need_replacement
  val s2_data       = mergeBlockData(s2_data_resp,s2_merge_data, s2_wordIdxOH)
  
  io.missBus.req.valid := s2_valid && s2_miss 
  io.missBus.req.bits.addr := s2_addr
  io.missBus.req.bits.waymask := s2_waymask
  io.missBus.req.bits.store := true.B
  io.missBus.req.bits.store_data  := s2_data
  io.missBus.req.bits.store_wmask := VecInit(Seq.fill(blockRows)("b1111".U))
  io.missBus.req.bits.burst := false.B
  io.missBus.stall := io.stall
  io.missBus.resp.ready := true.B
  
  io.miss_resp.valid         := s2_valid && s2_miss && s2_can_go_miss
  io.miss_resp.bits.miss     := s2_miss
  io.miss_resp.bits.addr     := s2_addr
  io.miss_resp.bits.tag      := s2_addr.asTypeOf(addrBundle).tag
  io.miss_resp.bits.waymask  := s2_waymask
  io.miss_resp.bits.data     := io.missBus.resp.bits.data  

  // s3 ctrl
  val s3_fire = Wire(Bool())
  val s3_valid = RegInit(false.B)  
  val s3_req = RegEnable(s2_req, s2_fire)
  val s3_addr = s3_req.addr
  val s3_idx = getIdx(s3_addr)
  val s3_miss = RegEnable(s2_miss, s2_fire)
  val s3_waymask = RegEnable(s2_waymask, s2_fire)
  val s3_wbdata = RegEnable(s2_wbdata, s2_fire)
  val s3_need_wb = RegEnable(s2_need_wb, s2_fire)
  val s3_can_go_miss = RegEnable(s2_can_go_miss, s2_fire)
  val s3_data = RegEnable(s2_data, s2_fire).asTypeOf(new DataBundle)
  val s3_retire = io.retire_store.valid && io.retire_store.bits.addr === s3_req.addr
  io.retire_store.ready := s3_valid
  s3_ready := !s3_valid || (!s3_need_wb || io.wb_resp.valid) 
  s3_fire := s3_valid && s3_ready

  when (s2_fire) {
    s3_valid := true.B
  }.elsewhen (s3_fire) {
    s3_valid := false.B
  }
  // s3 logic
  // (1) store hit can write data directly
  // (2) store miss need to wait for miss response then write
  // (3) write back evited line when replacement
  // always dirty when write no matter miss or not. 
  // io.dataWrite.req.bits.index   := s3_idx
  // io.dataWrite.req.valid        := s3_valid && s3_retire
  // io.dataWrite.req.bits.waymask := UIntToOH(s3_waymask)
  // io.dataWrite.req.bits.data    := s3_data
  io.dataWrite.apply(valid = s3_valid && s3_retire,data = s3_data,
                     index = s3_idx,waymask = UIntToOH(s3_waymask))


  io.metaWrite.req.bits.index   := s3_idx
  io.metaWrite.req.valid        := s3_valid && s3_retire
  io.metaWrite.req.bits.waymask := UIntToOH(s3_waymask)
  io.metaWrite.req.bits.data.tag := getTag(s3_req.addr)
  io.metaWrite.req.bits.data.valid := true.B
  io.metaWrite.req.bits.data.dirty := true.B // miss or store should set dirty

  io.wb_req.valid := s3_valid && s3_need_wb && s3_retire
  io.wb_req.bits.addr := s3_addr
  io.wb_req.bits.data := s3_wbdata
  io.wb_resp.ready := true.B
}

class L1LoadPipe (implicit val conf: Config) extends CacheModule  {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new L1Req))
    val resp = Decoupled(new L1Resp)
    val metaRead  = Flipped(new SRAMReadBus(new MetaBundle, nLines,nWays))
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

  //init replacement 
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
  io.missBus.req.bits.store_data := 0.U.asTypeOf(Vec(blockRows,UInt(rowBits.W)))
  io.missBus.req.bits.store_wmask := 0.U.asTypeOf(Vec(blockRows,UInt(rowBytes.W)))
  io.missBus.req.bits.store := false.B
  io.missBus.req.bits.burst := false.B
  io.missBus.stall := io.stall
  io.missBus.resp.ready := io.resp.ready

  // return 
  val miss_resp_data = io.missBus.resp.bits.data
  val resp_data = Mux(s2_hit, s2_hit_resp_data, Mux(s2_fix_miss, s2_fix_resp_data, miss_resp_data))
  // NOTE: icahce req should be 2 words as a line
  // val resp_data_out = resp_data.asTypeOf(Vec(blockRows,UInt(rowBits.W)))(getWordIdx(s2_addr))
  val resp_data_out = resp_data.asTypeOf(Vec(blockRows,UInt(rowBits.W)))(getWordIdx(s2_addr))
  // val bubbles = WireDefault(VecInit(Seq.fill(blockRows)(BUBBLE)))
  // val hold_resp_data = ResultHoldBypass(resp_data_out,s2_valid_out)
    
  io.resp.valid           := s2_fire
  io.resp.bits.data       := Mux(io.stall, BUBBLE ,resp_data_out)
  io.resp.bits.miss       := s2_miss
  io.resp.bits.exception  := 0.U
  io.resp.bits.write      := false.B

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

class L1Req (implicit val conf: Config) extends CacheBundle {
  val addr      = Input(UInt(conf.xlen.W))
  val store     = Input(Bool())
  val wdata     = Input(UInt(conf.xlen.W))
  val wmask     = Input(UInt(2.W))
}

class L1Resp(implicit val conf: Config) extends CacheBundle {
  val data      = Output((UInt(conf.xlen.W)))
  val exception = Output(UInt(5.W))
  val miss      = Output(Bool())
  val write     = Output(Bool())
}

class L1CahceBundle(implicit val conf: Config) extends CacheBundle  {
  val req           = Flipped(Decoupled(new L1Req))
  val resp          = Decoupled(new L1Resp)
  val stall         = Input(Bool())
  val axi_bus       = new AXI4Bus()
  val retire_store  = Flipped(Decoupled(new StoreRetireReq))
}


class L1Cache(implicit val conf: Config) extends CacheModule{ 
  val io = IO(new L1CahceBundle)

  val metas  = Module(new SRAMTemplate(new MetaBundle, nLines, nWays))
  val datas = Module(new SRAMTemplate(new DataBundle, nLines, nWays))

  val metaReadArb = Module(new Arbiter(new BundleA(nLines), 2))
  val metaWriteArb = Module(new RRArbiter(new BundleAW(new MetaBundle, nLines,nWays), 2))
  
  val dataReadArb = Module(new Arbiter(new BundleA(nLines), 2))
  val dataWriteArb = Module(new RRArbiter(new BundleAW(new DataBundle, nLines,nWays), 2))
  
  val axi_arb      = Module(new RRArbiter(new AXI4Req(),2))
  val miss_arb      = Module(new RRArbiter(new MissReq(),2))

  val loadPipe = Module(new L1LoadPipe)
  val mainPipe = Module(new MainPipe)
  val missUnit = Module(new MissUnit)
  val refillPipe = Module(new RefillPipe)
  val writeBackUnit = Module(new WriteBackUnit)
  val replacer = new RandomReplacement(nWays,nLines)
  val loadReplacer = new RandomReplacement(nWays,nLines)


  // meta read 
  metaReadArb.io.in(0) <> mainPipe.io.metaRead.req
  metaReadArb.io.in(1) <> loadPipe.io.metaRead.req
  metaReadArb.io.out <> metas.io.r.req
  mainPipe.io.metaRead.resp <> metas.io.r.resp
  loadPipe.io.metaRead.resp <> metas.io.r.resp

  // meta write
  metaWriteArb.io.in(0) <> refillPipe.io.metaWrite.req
  metaWriteArb.io.in(1) <> mainPipe.io.metaWrite.req
  metaWriteArb.io.out   <> metas.io.w.req

  // data read
  dataReadArb.io.in(0) <> mainPipe.io.dataRead.req
  dataReadArb.io.in(1) <> loadPipe.io.dataRead.req
  dataReadArb.io.out   <> datas.io.r.req
  mainPipe.io.dataRead.resp <> datas.io.r.resp
  loadPipe.io.dataRead.resp <> datas.io.r.resp

  // data write
  dataWriteArb.io.in(0) <> refillPipe.io.dataWrite.req
  dataWriteArb.io.in(1) <> mainPipe.io.dataWrite.req
  dataWriteArb.io.out   <> datas.io.w.req


  io.axi_bus.req   <> axi_arb.io.out
  axi_arb.io.in(0) <> missUnit.io.axi_bus.req   
  axi_arb.io.in(1) <> writeBackUnit.io.axi_bus.req 
  writeBackUnit.io.axi_bus.resp <> io.axi_bus.resp
  missUnit.io.axi_bus.resp <> io.axi_bus.resp
  
  // miss
  miss_arb.io.in(0) <> mainPipe.io.missBus.req
  miss_arb.io.in(1) <> loadPipe.io.missBus.req
  miss_arb.io.out   <> missUnit.io.bus.req
  missUnit.io.bus.stall := io.stall
  loadPipe.io.missBus.resp <> missUnit.io.bus.resp
  mainPipe.io.missBus.resp <> missUnit.io.bus.resp

  // refill 
  refillPipe.io.req <> missUnit.io.refill_req

  //main pipe 
  // val mainPipeArb = Module(new Arbiter(new MainPipeReq, 2))
  // mainPipeArb.io.in(0) <> missUnit.io.main_pipe
  // mainPipeArb.io.in(0) <> io.req
  val req_store = io.req.bits.store
  val req_valid = io.req.valid && !io.stall
  io.req.ready := mainPipe.io.req.ready && loadPipe.io.req.ready
  mainPipe.io.req.valid := req_store && req_valid
  mainPipe.io.req.bits := io.req.bits

  mainPipe.io.wb_req <> writeBackUnit.io.req
  mainPipe.io.retire_store <> io.retire_store
  mainPipe.io.replace_way.way := replacer.way
  mainPipe.io.stall := io.stall
  mainPipe.io.miss_resp.ready := true.B

  writeBackUnit.io.resp <> mainPipe.io.wb_resp

  // load pipe
  val io_req_dut = io.req.bits
  loadPipe.io.req.valid := !req_store && req_valid
  loadPipe.io.req.bits := io_req_dut
  loadPipe.io.stall           := io.stall
  loadPipe.io.replace_way.way := loadReplacer.way
  io.resp <> loadPipe.io.resp
}



