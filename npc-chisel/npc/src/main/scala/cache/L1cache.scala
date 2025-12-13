
package npc

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR

import npc.common._
import npc.common.UtilMethods._
import npc.Constants._
import java.rmi.server.UID

sealed trait HasL1Params{
  implicit val conf: Config
  val nSets:     Int = 1
  val nWays:     Int = 4
  val nLines:    Int = 8
  val rowBits:   Int = conf.xlen
  val rowBytes:  Int = rowBits/8
  val blockBytes:Int = 8
  val blockBits: Int = blockBytes*8
  val singleSet = nSets == 1
  val blockRows = blockBytes/rowBytes
  val blockIdx = log2Ceil(blockRows)
  def tagBits: Int = conf.xlen - idxBits - blockIdx - byteOffsetBits
  def idxBits: Int = log2Ceil(nLines) 
  def addrBits: Int = conf.xprlen
  def byteOffsetBits: Int = 2
  def addrBundle = new Bundle {
    val tag = UInt(tagBits.W)
    val index = UInt(idxBits.W)
    val wordIndex = UInt(blockIdx.W)
    val byteOffset = UInt(2.W)
  }

  def getIdx(addr: UInt) = addr.asTypeOf(addrBundle).index
  def getTag(addr: UInt) = addr.asTypeOf(addrBundle).tag
  def getWordIdx(addr: UInt) = addr.asTypeOf(addrBundle).wordIndex
  def mergePutData(old_data: UInt, new_data: UInt,wmask: UInt): UInt = {
    val full_mask = FillInterleaved(8, wmask)
    (old_data & ~full_mask) | (new_data & full_mask)
  }
}

abstract class CacheBundle extends Bundle with HasL1Params
abstract class CacheModule extends Module with HasL1Params
abstract class CacheConfig extends HasParameters with HasL1Params

sealed class MetaBundle (implicit val conf: Config)extends CacheBundle {
  val tag = Output(UInt(tagBits.W))
  val valid = Output(Bool())
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


class L1Req (implicit val conf: Config) extends CacheBundle {
  val addr = Input(UInt(conf.xlen.W))
  val rw   = Input(Bool())
}

class L1Resp(implicit val conf: Config) extends CacheBundle {
  // val data      = Output(Vec(blockRows,UInt(rowBits.W)))
  val data      = Output(UInt(rowBits.W))
  val exception = Output(UInt(5.W))
  val miss      = Output(Bool())
  val pc        = Output(UInt(conf.xprlen.W))
}

class L1CahceBundle(implicit val conf: Config) extends CacheBundle  {
  val req = Flipped(Decoupled(new L1Req))
  val resp = Decoupled(new L1Resp)
  val flush = Input(UInt(2.W))
  val port = new MemPortIo(conf.xlen)
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
  // val vaddr   = Input(UInt(conf.xprlen.W))
  // val idx     = Input(UInt(idxBits.W))
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
  io.metaWrite.req.bits.waymask := io.req.bits.wayMask
}

class MissReq (implicit val conf: Config) extends CacheBundle {
  val addr = Input(UInt(conf.xprlen.W))
  val vaddr = Input(UInt(conf.xprlen.W))
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

    io.axi_bus.req.bits := DontCare
    
    io.axi_bus.resp.ready := true.B
    val req_fire = Wire(Bool()) 
    val sRequesting :: sReceiving :: sRequestAgain :: sComplete :: Nil = Enum(4)

    val state   =   RegInit(sRequesting)
    val cacheLine = RegInit(VecInit(Seq.fill(blockRows)(0.U(conf.xlen.W))))
    val addr = ResultHoldBypass(io.bus.req.bits.addr,io.bus.req.valid)
    val offset = RegInit(0.U(blockIdx.W))
    val store_mask = io.bus.req.bits.store_wmask
    val store_data = io.bus.req.bits.store_data
    req_fire := io.bus.req.fire

    val refill_store_data = Wire(Vec(blockRows,UInt(rowBits.W)))
    val refill_store_mask = Wire(Vec(blockRows,UInt(rowBytes.W)))

    for(i <- 0 until blockRows) {
      // refill_store_data(i) := store_data((i+1)*rowBits-1, i*rowBits)
      // refill_store_mask(i) := store_mask((i+1)*rowBytes-1, i*rowBytes)
      refill_store_data(i) := store_data(i)
      refill_store_mask(i) := store_mask(i)
    }
    val is_requesting = state === sRequesting && (io.bus.req.valid) 
    val is_request_again = state === sRequestAgain 
    io.axi_bus.req.valid := (is_requesting || is_request_again) 
    io.axi_bus.req.bits.ren         := io.axi_bus.req.valid
    when (io.bus.req.bits.burst){
      io.axi_bus.req.bits.raddr       := Cat(addr(conf.xprlen-1,blockIdx+byteOffsetBits),0.U(blockIdx.W+byteOffsetBits.W))
      io.axi_bus.req.bits.burst       := BURST_INCR
      io.axi_bus.req.bits.burstlen    := blockIdx.U
    }.otherwise {
      io.axi_bus.req.bits.raddr       := Cat(addr(conf.xprlen-1,blockIdx+byteOffsetBits),offset,0.U(byteOffsetBits.W))
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


// class LoadPipeResp (implicit val conf: Config)extends CacheBundle {
//   val data = Output(Vec(blockRows,UInt(rowBits.W)))
//   val exception = Output(UInt(5.W))
//   val miss = Output(Bool())
// }



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
  // mainPipeArb.io.in(0) <> missUnit.io.main_pipe
  mainPipeArb.io.in(0) <> io.req
  mainPipe.io.req <> mainPipeArb.io.out
  mainPipe.io.missBus <> missUnit.io.bus

  // load pipe
  missUnit.io.bus <> loadPipe.io.missBus
  loadPipe.io.req <> io.req


}



class RandomReplacement (nWays:Int, nLines:Int)(implicit val conf: Config) { 
  def nBits = 16
  private val lfsr = LFSR(nBits,false.B)
  def way = Random(nWays,lfsr)
  def access(touch_way: UInt) = {}
  def get_replace_way(idx: UInt): UInt = way

}

object Random {
  def apply(mod: Int, rand: UInt): UInt = {
    require(isPow2(mod))
    val modBits = log2Ceil(mod)-1
    rand(modBits,0)
  }
}