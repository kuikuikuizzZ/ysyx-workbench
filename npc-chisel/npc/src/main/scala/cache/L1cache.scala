
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
  val nLines:    Int = 64
  val rowLengths:   Int = 128
  val singleSet = nSets == 1
  def tagBits: Int = conf.xlen - log2Ceil(nSets) - log2Ceil(nLines) - rowLengths - 2
  def idxBits: Int = log2Ceil(nLines) 
  def offsetBits: Int = log2Ceil(rowLengths/conf.xlen)
  def addrBits: Int = conf.xprlen
  def addrBundle = new Bundle {
    val tag = UInt(tagBits.W)
    val index = UInt(idxBits.W)
    val wordIndex = UInt(offsetBits.W)
    val byteOffset = UInt(2.W)
  }

  def getIdx(addr: UInt) = addr.asTypeOf(addrBundle).index
  def getWordIdx(addr: UInt) = addr.asTypeOf(addrBundle).wordIndex
 
}

abstract class CacheBundle(implicit val conf: Config) extends Bundle with HasL1Params
abstract class CacheModule(implicit val conf: Config) extends Module with HasL1Params

class MetaBundle extends CacheBundle {
  val tag = Output(UInt(tagBits.W))
  val valid = Output(Bool())
  def apply(tag: UInt,valid : Bool) = {
    this.tag := tag
    this.valid := valid 
    this
  }
}

class DataBundle extends CacheBundle {
  val data = Output(UInt(conf.xlen.W))

  def apply(data: UInt) = {
    this.data := data
    this
  }
}

class BundleA(nLines: Int) extends Bundle {
  val index = Output(UInt(log2Ceil(nLines).W))
}

class BundleR[T <: Data](gen: T,way: Int) extends Bundle {
  val data = Input(Vec(way,gen))
}

class BundleAW[T <: Data](gen: T, nLines: Int,way: Int) extends Bundle {
  val index = Output(UInt(log2Ceil(nLines).W))
  val waymask = Output(UInt(way.W))
  val data = Output(Vec(way,gen))
}

class SRAMWriteBus[T <: Data](gen: T, nLines: Int, way: Int) extends Bundle {
  val req = Decoupled(new BundleAW(gen,nLines,way))
}

class SRAMReadBus[T <: Data](gen: T, nLines: Int, way: Int) extends Bundle {
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

class MissReq (implicit val conf: Config) extends CacheBundle {
  val addr = Input(UInt(conf.xprlen.W))
}

class MissResp (implicit val conf: Config) extends CacheBundle {
  val data = Output(UInt(conf.xprlen.W))
}

class MissUnitBus(implicit val conf: Config) extends CacheBundle {
  val req = Decoupled(new MissReq)
  val resp = Decoupled(new MissResp)
} 

class MissUnit(implicit val conf: Config) extends CacheModule {
  val io = IO(new Bundle {
    val bus = new MissUnitBus
    val port      = new MemPortIo(conf.xlen)  // should use tilelink or axi?
    val mainPipe  = Flipped(Decoupled(new MainPipeReq))
  })

  val miss_valid = io.bus.req.valid
  when(miss_valid){
    // memport is not sufficient, need to add burst support?
    io.port.req.bits.addr := io.bus.req.bits.addr
    io.port.req.valid := true.B
  }

  when(io.port.resp.valid){
    io.bus.resp.bits.data := io.port.resp.bits.data
    io.bus.resp.valid := true.B
    io.mainPipe.req.valid := true.B
    io.mainPipe.req.bits.addr := io.bus.req.bits.addr
    io.mainPipe.req.bits.mask := Fill(conf.xlen/8,1.U)
    io.mainPipe.req.bits.wdata := io.port.resp.bits.data
  }
}


class LoadPipeResp extends CacheBundle {
  val data = Output(UInt(conf.xlen.W))
  val exception = Output(UInt(5.W))
  val resp_miss = Output(Bool())
}


class LoadPipe (implicit val conf: Config) extends CacheModule  {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new L1Req))
    val resp = Decoupled(new LoadPipeResp)
    val metaRead = new SRAMReadBus(new MetaBundle, nLines,nWays)
    val dataRead  = new SRAMReadBus(new DataBundle, nLines,nWays)
    val missBus   = Filpped(new MissUnitBus)
  })

  val s0_req = io.req
  val s0_addr = io.req.bits.addr
  val s0_idx = getIdx(s0_addr)
  io.metaRead.req.bits.index := s0_idx
  io.metaRead.req.valid := s0_req.valid
  s0_req.ready := io.metaRead.req.ready
  val s1_meta  = io.metaRead.resp.bits.data
  val hitVec = WireInit(VecInit(Seq.fill(nWays)(false.B)))

  for (i <- 0 until nWays) {
    hitVec(i) := s1_meta(i).valid && (s1_meta(i).tag === s0_addr.asTypeOf(addrBundle).tag)
  }
  
  // stage 1
  val s1_hit = hitVec.asUInt.orR
  val s1_miss = !s1_hit  
  val waymask = WireInit(0.U(nWays.W))
  io.dataRead.req.bits.index := s0_idx
  io.dataRead.req.valid := s0_req.valid && s1_hit

  // miss how to notify loadPipe to reload the data after refilled?
  io.missBus.req.bits.addr := s0_addr
  
  when (s1_miss){
    io.missBus.req.valid := true.B
    io.missBus.req.bits := s1_addr
    s2_miss := RegNext(s1_miss, false.B)
  }
  // stage 2
  val s2_resp_data = io.dataRead.resp.bits.data
  io.resp.valid := RegNext(s1_hit)
  io.resp.bits.data := Mux1H(waymask, s2_resp_data)
  io.resp.bits.resp_miss := s2_miss
}

class MainPipeReq (implicit val conf: Config) extends CacheBundle {
  val addr = Input(UInt(conf.xprlen.W))
  val mask = Input(UInt((conf.xlen/8).W))
  val wdata = Input(UInt(conf.xlen.W))
}



class MainPipe (implicit val conf: Config) extends CacheModule  {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new MainPipeReq))
    val metaWrite = new SRAMReadBus(new MetaBundle, nLines,nWays)
    val metaRead = new SRAMReadBus(new MetaBundle, nLines,nWays)
    val dataRead = new SRAMReadBus(new DataBundle, nLines,nWays)
    val dataWrite = new SRAMReadBus(new DataBundle, nLines,nWays)
    val missBus = new MissUnitBus
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
  for (i <- 0 until nWays) {
    hitVec(i) := s1_meta(i).valid && (s1_meta(i).tag === s0_addr.asTypeOf(addrBundle).tag)
  }
  io.dataWrite.req.bits.index   := s0_idx
  io.dataWrite.req.valid        := s1_valid
  io.dataWrite.req.bits.waymask := UIntToOH(s0_req.bits.mask)
  io.dataWrite.req.bits.data    := RegNext(s0_req.bits.wdata)
  when(s1_valid){
    io.metaWrite.req.bits.index := s0_idx
    io.metaWrite.req.bits.data := MetaBundle(s0_addr.asTypeOf(addrBundle), true.B)
    io.metaWrite.req.valid := true.B
  }
  when(s1_miss){
    io.missBus.req.bits.addr := s0_addr
    io.missBus.req.valid := s1_valid
  }
}




class L1Cache(implicit val conf: Config) extends Module with HasL1Params{ 
    val io = IO(new L1CahceBundle)

  val metas  = Module(new CacheSRAMTemplate(new MetaBundle, nLines, nWays))
  val datas = Module(new CacheSRAMTemplate(new DataBundle, nLines, nWays))

  val metaReadArb = Module(new Arbiter(new SRAMReadBus(new MetaBundle, nLines,nWays), 2))
  val metaWriteArb = Module(new Arbiter(new SRAMReadBus(new MetaBundle, nLines,nWays), 2))
  
  val dataReadArb = Module(new Arbiter(new SRAMReadBus(new DataBundle, nLines,nWays), 2))
  val dataWriteArb = Module(new Arbiter(newSRAMReadBus(new DataBundle, nLines,nWays), 2))
  val loadPipe = Module(new LoadPipe)
  val mainPipe = Module(new MainPipe)
  val missUnit = Module(new MissUnit)

  val mainPipeArb = Module(new Arbiter(new MainPipeReq, 2))

  // meta read 
  metaReadArb.io.in(0) <> loadPipe.io.metaRead
  metaReadArb.io.in(1) <> mainPipe.io.metaRead
  metaReadArb.io.out <> metas.io.r

  // meta write
  metaArb.io.in(0).valid := false.B
  metaArb.io.in(0).bits := metaArb.io.in(5).bits
  metaArb.io.in(0).bits.req := true.B
  metaArb.io.in(0).bits.way_en := ~0.U(nWays.W)

  metaWriteArb.io.in(0) <> mainPipe.io.metaRead
  metaWriteArb.io.out   <> metas.io.w

  // data read
  dataReadArb.io.in(0) <> loadPipe.io.dataRead.req
  dataReadArb.io.in(0) <> mainPipe.io.dataRead.req
  dataReadArb.io.out   <> datas.io.r

  // data write
  dataWriteArb.io.in(0) <> mainPipe.io.dataWrite
  dataWriteArb.io.out   <> datas.io.w

  //main pipe 
  mainPipeArb.io.in(0) <> missUint.io.mainPipe
  mainPipeArb.io.in(1) <> io.req
  mainPipe.io.out <> mainPipeArb.io.req
  mainPipe.io.missBus <> missUnit.io.bus

  // load pipe
  missUnit.io.bus <> mainPipe.io.missBus
  loadPipe.io.req <> io.req


}

class CacheSRAMTemplate[T <: Data](typ: T, line: Int, ways: Int )(implicit val conf: Config) extends Module with HasL1Params {
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