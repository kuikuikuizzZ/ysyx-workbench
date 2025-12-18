
package npc

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR

import npc.common._
import npc.Constants._
import java.rmi.server.UID

class BundleA(nLines: Int) (implicit val conf: Config)extends CacheBundle {
  val index = Input(UInt(log2Ceil(nLines).W))
  def apply(index: UInt) = {
    this.index := index
    this
  }
}

class BundleR[T <: Data](gen: T,way: Int) (implicit val conf: Config) extends CacheBundle {
  val data = Output(Vec(way,gen))
}

class BundleAW[T <: Data](gen: T, nLines: Int,way: Int)(implicit val conf: Config) extends CacheBundle {
  val index = Input(UInt(log2Ceil(nLines).W))
  val waymask = Input(UInt(way.W))
  // val wMask = Input(UInt(blockRows.W))
  val data = Input(gen)

   def apply(index: UInt,waymask: UInt,data: T) = {
    this.index := index
    this.waymask := waymask
    this.data := data
    this
  }
}

class SRAMWriteBus[T <: Data](gen: T, nLines: Int, way: Int)(implicit val conf: Config) extends CacheBundle {
  val req = Flipped(Decoupled(new BundleAW(gen,nLines,way)))
  def apply(valid: Bool, data: T, index: UInt, waymask: UInt) = {
    this.req.bits.apply(data = data, index = index, waymask = waymask)
    this.req.valid := valid
    this
  }
}

class SRAMReadBus[T <: Data](gen: T, nLines: Int, way: Int) (implicit val conf: Config)extends CacheBundle {
  val req = Flipped(Decoupled(new BundleA(nLines)))
  val resp = Decoupled(new BundleR(gen,way))
  def apply(valid:Bool,index: UInt) = {
    this.req.bits.apply(index) 
    this.req.valid := valid
    this
  }
}



class SRAMTemplate[T <: Data](typ: T, line: Int, ways: Int )(implicit val conf: Config) extends CacheModule {
  val io = IO(new Bundle {
    val r = new SRAMReadBus(typ, line, ways)
    val w = new SRAMWriteBus(typ, line, ways)
  })
  
  // 正确的内存定义：Vec类型
  val mem = SyncReadMem(line, Vec(ways, UInt(typ.getWidth.W)))
  
  // 简化的复位逻辑
  val resetState = RegInit(true.B)
  val resetCounter = RegInit(0.U(log2Ceil(line+1).W))
  
  when(resetState) {
    resetCounter := resetCounter + 1.U
    when(resetCounter >= (line-1).U) {
      resetState := false.B
    }
  }
//   val resetState = false.B
//   val read_valid = io.r.req.valid 
//   val read_idx = io.r.req.bits.index
//   val write_valid = io.w.req.valid 
//   val write_idx = io.w.req.bits.index
//   val write_mask = io.w.req.bits.waymask
//   val write_data = io.w.req.bits.data.asUInt

  // 读逻辑
  val read_valid = io.r.req.valid && !resetState
  val read_idx = Mux(resetState, resetCounter, io.r.req.bits.index)


  when(read_valid) {
    io.r.resp.bits.data := mem.read(read_idx).asTypeOf(Vec(ways, typ))
  }.otherwise {
    io.r.resp.bits.data := DontCare
  }
  
  io.r.resp.valid := RegNext(read_valid, false.B)
  io.r.req.ready := !resetState
  
  // 写逻辑
  val write_valid = io.w.req.valid && !resetState
  val write_idx = Mux(resetState, resetCounter, io.w.req.bits.index)
  val write_mask = Mux(resetState, Fill(ways, 1.U), io.w.req.bits.waymask)
  val write_data = Mux(resetState, 0.U, io.w.req.bits.data.asUInt)

  

  when(write_valid) {
    val current = mem.read(write_idx)  // 读出现有数据
    val updated = Wire(Vec(ways, UInt(typ.getWidth.W)))
    
    for (i <- 0 until ways) {
      updated(i) := Mux(write_mask(i), write_data, current(i))
    }
    
    mem.write(write_idx, updated)
  }
  
  io.w.req.ready := !resetState
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