
package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._
import java.rmi.server.UID

sealed trait HasL1Params{
  implicit val conf: Config
  val nSets:     Int = 1
  val nWays:     Int = 4
  val nLines:    Int = 8
  val rowLengths:   Int = 32
  val singleSet = nSets == 1
  val wordsPerRow = rowLengths / conf.xlen
  def tagBits: Int = conf.xlen - log2Ceil(nSets) - log2Ceil(nLines) - offsetBits - byteOffsetBits
  def idxBits: Int = log2Ceil(nLines) 
  def offsetBits: Int = log2Ceil(rowLengths/conf.xlen) 
  def addrBits: Int = conf.xprlen
  def byteOffsetBits: Int = 2
  def addrBundle = new Bundle {
    val tag = UInt(tagBits.W)
    val index = UInt(idxBits.W)
    val wordIndex = UInt(offsetBits.W)
    val byteOffset = UInt(2.W)
  }

  def getIdx(addr: UInt) = addr.asTypeOf(addrBundle).index
  def getWordIdx(addr: UInt) = addr.asTypeOf(addrBundle).wordIndex
  def getTag(addr: UInt) = addr.asTypeOf(addrBundle).tag
}

abstract class CacheBundle extends Bundle with HasL1Params
abstract class CacheModule extends Module with HasL1Params

class MetaBundle (implicit val conf: Config)extends CacheBundle {
  val tag = Output(UInt(tagBits.W))
  val valid = Output(Bool())
  def apply(tag: UInt,valid : Bool) = {
    this.tag := tag
    this.valid := valid 
    this
  }
}

class DataBundle(implicit val conf: Config) extends CacheBundle {
  val data = Output(UInt(rowLengths.W))

  def apply(data: UInt) = {
    this.data := data
    this
  }
}

class BundleA(nLines: Int) (implicit val conf: Config)extends CacheBundle {
  val index = Output(UInt(log2Ceil(nLines).W))
}

class BundleR[T <: Data](gen: T,way: Int) (implicit val conf: Config) extends CacheBundle {
  val data = Input(Vec(way,gen))
}

class BundleAW[T <: Data](gen: T, nLines: Int,way: Int)(implicit val conf: Config) extends CacheBundle {
  val index = Output(UInt(log2Ceil(nLines).W))
  val waymask = Output(UInt(way.W))
  val wMask = Output(UInt(wordsPerRow.W))
  val data = Output(Vec(way,gen))
}

class SRAMWriteBus[T <: Data](gen: T, nLines: Int, way: Int)(implicit val conf: Config) extends CacheBundle {
  val req = Decoupled(new BundleAW(gen,nLines,way))
}

class SRAMReadBus[T <: Data](gen: T, nLines: Int, way: Int) (implicit val conf: Config)extends CacheBundle {
  val req = Decoupled(new BundleA(nLines))
  val resp = Decoupled(new BundleR(gen,way))
}


class L1Req(implicit conf: Config) extends Bundle {
  val addr = UInt(conf.xlen.W)
  val rw   = Bool()
}

class L1Resp(implicit conf: Config) extends Bundle {
  val data = UInt(conf.xlen.W)
  val exception = UInt(5.W)
}

class L1CahceBundle(implicit val conf: Config) extends CacheBundle  {
  val req = Flipped(Decoupled(new L1Req))
  val resp = Decoupled(new L1Resp)
  val flush = Input(UInt(2.W))
  val port = new MemPortIo(conf.xlen)
}

class RefillReq (implicit val conf: Config)extends CacheBundle {
  val addr = Input(UInt(conf.xprlen.W))
  val vaddr = Input(UInt(conf.xprlen.W))
  val wayMask = Input(UInt(nWays.W))
  val idx = Input(UInt(idxBits.W))
  val wordMask = Input(UInt(wordsPerRow.W))
  val data = Input(UInt(rowLengths.W))
}

class RefillResp (implicit val conf: Config)extends CacheBundle {
  val data = Output(Bool())
}

class ReplaceReq (implicit val conf: Config) extends CacheBundle {
  val idx = Input(UInt(idxBits.W))
  val way = Input(UInt(nWays.W))
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
    val req = Decoupled(new RefillReq)
    val resp = Decoupled(new RefillResp)
    val metaRead = new SRAMReadBus(new MetaBundle, nLines,nWays)
    val dataRead = new SRAMReadBus(new DataBundle, nLines,nWays)
    val metaWrite = new SRAMWriteBus(new MetaBundle, nLines,nWays)
    val dataWrite = new SRAMWriteBus(new DataBundle, nLines,nWays)
  })
  io.req.ready := true.B
  io.resp.valid := io.req.fire

  val idx = getIdx(io.req.bits.addr)
  val tag = getTag(io.req.bits.addr)
  val valids = VecInit(Seq.fill(nWays)(true.B))
  io.dataWrite.req.valid := io.req.valid
  io.dataWrite.req.bits.index := idx
  io.dataWrite.req.bits.data := io.req.bits.data
  io.dataWrite.req.bits.waymask := io.req.bits.wayMask
  io.dataWrite.req.bits.wMask := io.req.bits.wordMask

  io.metaWrite.req.valid := io.req.valid
  io.metaWrite.req.bits.index := idx
  // io.metaWrite.req.bits.data := MetaBundle(tag,valids)

}

class MissReq (implicit val conf: Config) extends CacheBundle {
  val addr = Input(UInt(conf.xprlen.W))
  val vaddr = Input(UInt(conf.xprlen.W))
  val waymask = Input(UInt(nWays.W)) 

  val store_data = Input(UInt(rowLengths.W))
  val store_wmask = Input(UInt(wordsPerRow.W))
  val store = Input(Bool())
}

class MissResp (implicit val conf: Config) extends CacheBundle {
  val data = Output(UInt(rowLengths.W))
  val resp = Output(UInt(2.W)) // OKAY or SLVERR
  val addr = Output(UInt(conf.xprlen.W))
}

class MissUnitBus(implicit val conf: Config) extends CacheBundle {
  val req = Flipped(DecoupledIO(new MissReq))
  val resp = Decoupled(new MissResp)
} 

class MissUnit(implicit val conf: Config) extends CacheModule {
    val io = IO(new Bundle {
        val bus = new MissUnitBus
        val axi_bus = new AXI4Bus()
        val main_pipe = Flipped(new MainPipeReq)
        val debug = new Bundle{
          val state = Output(UInt(2.W))
        }
    })

    io.bus.req := DontCare
    io.bus.resp := DontCare
    io.main_pipe := DontCare
    io.axi_bus.req := DontCare
    io.axi_bus.resp.ready := true.B
    val req_fire = Wire(Bool()) 
    val sRequesting :: sReceiving :: sComplete :: Nil = Enum(3)

    val state   =   RegInit(sRequesting)
    val cacheLine = RegInit(VecInit(Seq.fill(wordsPerRow)(0.U(conf.xlen.W))))
    val addr = RegEnable(io.bus.req.bits.addr,0.U, req_fire)
    val offset = RegInit(0.U(offsetBits.W))
    req_fire := io.bus.req.fire

    when (conf.ICacheEnableBurst){
      io.axi_bus.req.valid           := io.bus.req.valid
      io.axi_bus.req.bits.raddr      := Cat(addr(conf.xprlen-1,offsetBits+byteOffsetBits),0.U(offsetBits.W+byteOffsetBits.W))
      io.axi_bus.req.bits.ren        := true.B
      io.axi_bus.req.bits.burst      := BURST_INCR
      io.axi_bus.req.bits.burstlen   := Mux(conf.ICacheEnableBurst,conf.burstLength.U,0.U)
    }.otherwise{
      io.axi_bus.req.valid       := io.bus.req.valid
      io.axi_bus.req.bits.raddr   := Cat(addr(conf.xprlen-1,offsetBits+byteOffsetBits),offset,0.U(byteOffsetBits.W))
      io.axi_bus.req.bits.ren    := true.B
      io.axi_bus.req.bits.burstlen := 0.U
      io.axi_bus.req.bits.burst := BURST_FIXED
    }

    when(state === sRequesting ){
      when(io.bus.req.valid && io.axi_bus.req.ready) {
        offset := 0.U
        state := sReceiving 
    }}    
    .elsewhen(state === sReceiving) {
        when(io.axi_bus.resp.valid) {
            offset := offset + 1.U
            cacheLine(offset) := io.axi_bus.resp.bits.data // 存储子块
            // 检查是否完成
            when(offset === (wordsPerRow-1).U) {
                state := sComplete
            }.elsewhen(conf.ICacheEnableBurst && !io.axi_bus.resp.bits.last ) { 
                state := sReceiving 
            }.otherwise {
                state :=  sRequesting // 继续请求下一子块
            }
        }
    }.elsewhen(state === sComplete) { 
        state   := sRequesting
    }
    
  io.bus.req.ready := io.axi_bus.req.ready && (state === sRequesting)
  io.bus.resp.valid := state === sComplete
  io.bus.resp.bits.data := cacheLine.asUInt
  io.bus.resp.bits.resp := 0.U // OKAY
  io.bus.resp.bits.addr := addr

  /////// debug
  io.debug.state := state
}


class LoadPipeResp (implicit val conf: Config)extends CacheBundle {
  val data = Output(UInt(conf.xlen.W))
  val exception = Output(UInt(5.W))
  val resp_miss = Output(Bool())
}

class ReplacePolicy (implicit val conf: Config)extends CacheBundle {
  val way = Output(UInt(nWays.W))
  val idx = Output(UInt(idxBits.W))
}


class LoadPipe (implicit val conf: Config) extends CacheModule  {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new L1Req))
    val resp = Decoupled(new LoadPipeResp)
    val metaRead = new SRAMReadBus(new MetaBundle, nLines,nWays)
    val dataRead  = new SRAMReadBus(new DataBundle, nLines,nWays)
    val missBus   = Flipped(new MissUnitBus)
    val replace_access = Decoupled(new ReplacePolicy)
    val lsu_s1_kill = Input(Bool())
  })

  // stage 0 ctrl 
  val s1_ready = Wire(Bool())
  val s0_req = io.req.bits
  val s0_valid = io.req.fire
  val s0_fire = s0_valid && s1_ready
  io.req.ready := io.metaRead.req.ready && s1_ready

  // stage 0 pipeline
  val s0_addr = io.req.bits.addr
  io.metaRead.req.valid := s0_valid
  io.metaRead.req.bits.index := getIdx(s0_addr)
  
  // stage 1 ctrl 
  val s2_ready = Wire(Bool())
  val s1_valid = RegInit(false.B)
  val s1_fire = s1_valid && s2_ready
  s1_ready := !s1_valid || s1_fire

  // stage 1 pipeline 
  val s1_req = RegEnable(s0_req, s0_fire)
  val s1_addr = RegEnable(s0_addr, s0_fire)
  val s1_meta  = io.metaRead.resp.bits.data
  val s1_hit_vec = WireInit(VecInit(Seq.fill(nWays)(false.B)))
  val s1_tag_match_way = WireInit(VecInit(Seq.fill(nWays)(false.B)))
  val s1_idx = getIdx(s1_addr)
  for (i <- 0 until nWays) {
    s1_tag_match_way(i) := (s1_meta(i).tag === s1_addr.asTypeOf(addrBundle).tag)
    s1_hit_vec(i) := s1_meta(i).valid && (s1_meta(i).tag === s1_addr.asTypeOf(addrBundle).tag)
  }  
  val s1_hit = s1_hit_vec.asUInt.orR && s1_valid
  val s1_miss = !s1_hit && s1_valid

  io.dataRead.req.valid := s1_fire
  io.dataRead.req.bits.index := s1_idx
  // io.dataRead.req.bits.waymask := s1_hit_vec

  // init replacement 
  io.replace_access.valid := s1_valid 
  io.replace_access.bits.way := s1_hit_vec.asUInt
  io.replace_access.bits.idx := s1_idx

  when (s0_fire){
    s1_valid := true.B 
  } .elsewhen (s1_fire){
    s1_valid := false.B
  }

  // stage 2
  val s2_valid = RegInit(false.B)
  val s2_tag_match_way = RegEnable(s1_tag_match_way,s1_fire)
  val s2_hit_vec = RegEnable(s1_hit_vec,s1_fire)
  val s2_hit = RegEnable(s1_hit, s1_fire)
  val s2_idx = RegEnable(s1_idx, s1_fire)
  val s2_addr = RegEnable(s1_addr, s1_fire)
  val s2_miss = RegEnable(s1_miss, s1_fire)
  val s2_resp_data = io.dataRead.resp.bits.data
  val s2_fire = s2_valid
  s2_ready := true.B

  when (s1_fire) { s2_valid := !io.lsu_s1_kill }
  .elsewhen(io.resp.fire) { s2_valid := false.B }

  // stage 2 miss 
  io.missBus.req.valid := s2_valid && s2_miss 
  io.missBus.req.bits.addr := s2_addr

  io.resp.valid := s2_valid
  io.resp.bits.data := Mux1H(s2_hit_vec, s2_resp_data)
  io.resp.bits.resp_miss := s2_miss

}



class MainPipe (implicit val conf: Config) extends CacheModule  {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new MainPipeReq))
    val metaRead = new SRAMReadBus(new MetaBundle, nLines,nWays)
    val dataRead = new SRAMReadBus(new DataBundle, nLines,nWays)
    val metaWrite = new SRAMWriteBus(new MetaBundle, nLines,nWays)
    val dataWrite = new SRAMWriteBus(new DataBundle, nLines,nWays)
    val missBus =  Flipped(new MissUnitBus)
  })
  val s0_req = io.req
  val s0_addr = io.req.bits.addr
  val s0_idx = getIdx(s0_addr)
  io.metaRead.req.bits.index := s0_idx
  io.metaRead.req.valid := s0_req.valid
  s0_req.ready := io.metaRead.req.ready
  val s1_valid = RegNext(s0_req.valid, false.B)
  val s1_meta  = io.metaRead.resp.bits.data
  val s1_hit = hitVec.asUInt.orR
  val s1_miss = !s1_hit
  val hitVec = WireInit(VecInit(Seq.fill(nWays)(false.B)))
  // val metas = Wire(new MetaBundle(s0_addr.asTypeOf(addrBundle), true.B))
  for (i <- 0 until nWays) {
    hitVec(i) := s1_meta(i).valid && (s1_meta(i).tag === s0_addr.asTypeOf(addrBundle).tag)
  }
  io.dataWrite.req.bits.index   := s0_idx
  io.dataWrite.req.valid        := s1_valid
  io.dataWrite.req.bits.waymask := UIntToOH(s0_req.bits.mask)
  io.dataWrite.req.bits.data    := RegNext(s0_req.bits.wdata)
  when(s1_valid){
    io.metaWrite.req.bits.index := s0_idx
    // io.metaWrite.req.bits.data := metas
    io.metaWrite.req.valid := true.B
  }
  when(s1_miss){
    io.missBus.req.bits.addr := s0_addr
    io.missBus.req.valid := s1_valid
  }
}




class L1Cache(implicit val conf: Config) extends CacheModule{ 
    val io = IO(new L1CahceBundle)

  val metas  = Module(new CacheSRAMTemplate(new MetaBundle, nLines, nWays))
  val datas = Module(new CacheSRAMTemplate(new DataBundle, nLines, nWays))

  val metaReadArb = Module(new Arbiter(new SRAMReadBus(new MetaBundle, nLines,nWays), 2))
  val metaWriteArb = Module(new Arbiter(new SRAMWriteBus(new MetaBundle, nLines,nWays), 2))
  
  val dataReadArb = Module(new Arbiter(new SRAMReadBus(new DataBundle, nLines,nWays), 2))
  val dataWriteArb = Module(new Arbiter(new SRAMWriteBus(new DataBundle, nLines,nWays), 2))
  val loadPipe = Module(new LoadPipe)
  val mainPipe = Module(new MainPipe)
  val missUnit = Module(new MissUnit)

  val mainPipeArb = Module(new Arbiter(new MainPipeReq, 2))

  // meta read 
  metaReadArb.io.in(0) <> loadPipe.io.metaRead
  metaReadArb.io.in(1) <> mainPipe.io.metaRead
  metaReadArb.io.out <> metas.io.r

  // meta write
  metaWriteArb.io.in(0).valid := false.B
  metaWriteArb.io.in(0).bits := metaWriteArb.io.in(5).bits


  metaWriteArb.io.in(0) <> mainPipe.io.metaWrite
  metaWriteArb.io.out   <> metas.io.w

  // data read
  dataReadArb.io.in(0) <> loadPipe.io.dataRead.req
  dataReadArb.io.in(0) <> mainPipe.io.dataRead.req
  dataReadArb.io.out   <> datas.io.r

  // data write
  dataWriteArb.io.in(0) <> mainPipe.io.dataWrite
  dataWriteArb.io.out   <> datas.io.w

  //main pipe 
  mainPipeArb.io.in(0) <> missUnit.io.main_pipe
  mainPipeArb.io.in(1) <> io.req
  mainPipe.io.req <> mainPipeArb.io.out
  mainPipe.io.missBus <> missUnit.io.bus

  // load pipe
  missUnit.io.bus <> loadPipe.io.missBus
  loadPipe.io.req <> io.req


}

class CacheSRAMTemplate[T <: Data](typ: T, line: Int, ways: Int )(implicit val conf: Config) extends CacheModule {
  val io = IO(new Bundle {
    val r = Flipped(new SRAMReadBus(typ, line, ways))
    val w = Flipped(new SRAMWriteBus(typ, line, ways))
  })
  val wordtype = UInt(typ.getWidth.W)
  val array = SyncReadMem(line, Vec(ways, wordtype))

  val (resetState, resetLine) = (WireInit(false.B), WireInit(0.U))
  val _resetState = RegInit(true.B)
  val (_resetLine, resetFinish) = Counter(_resetState, nLines)
  when (resetFinish) { _resetState := false.B } 
  resetState := _resetState
  resetLine := _resetLine

  // Read logic
  val r_valid = io.r.req.valid && !resetState
  val rdata = array.read(io.r.req.bits.index, r_valid).map(_.asTypeOf(typ))
  io.r.resp.bits.data := VecInit(rdata)
  io.r.req.ready := !resetState 
  io.r.resp.valid := RegNext(r_valid, false.B)
  
  // Write logic
  val w_valid = io.w.req.valid && !resetState
  val windex  = Mux(resetState, resetLine, io.w.req.bits.index)
  val waymask = Mux(resetState, Fill(ways, 1.U), io.w.req.bits.waymask)
  val wword   = Mux(resetState, 0.U.asTypeOf(wordtype), io.w.req.bits.data.asUInt)
  val wdata   = VecInit(Seq.fill(ways)(wword))
  when(w_valid){
      array.write(windex, wdata, waymask.asBools)
  }
  io.w.req.ready := !resetState && !r_valid


}