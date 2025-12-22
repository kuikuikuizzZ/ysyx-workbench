
package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc.common.UtilMethods._
import npc.Constants._
// import utils._
// import difftest._


sealed trait HasBPUParams{
    implicit val conf: Config
    val nBTBEntries:    Int = 256   
    val nRas       :    Int = 16
    val counterBits:    Int = 2
    val historyBits:    Int = 3
    val historyLength:  Int = 8
    // val nBTBWays:       Int = 4
    val idxBits   = log2Up(nBTBEntries)
    val tagBits = conf.xprlen - idxBits 
    val groupSize = conf.fetchGroupBytes/4   // default 2
    val groupBits = log2Up(groupSize)
    def addrBundle = new Bundle {
        val tag = UInt(tagBits.W)
        val index = UInt(idxBits.W)
        val groupOffset = UInt(groupBits.W)
        val byteOffset = UInt(2.W)
    }
    def getIdx(addr: UInt) = addr.asTypeOf(addrBundle).index
    def getTag(addr: UInt) = addr.asTypeOf(addrBundle).tag
    def getGroupOffset(addr: UInt) = addr.asTypeOf(addrBundle).groupOffset
    def BtbEntry() = new Bundle {
        val tag = addrBundle.tag
        val target = UInt(conf.xprlen.W)
        val valid = Bool()
        val redirect_type = UInt(RD_JAL.getWidth.W)
    }
    
}

abstract class BPUModule extends Module with HasBPUParams
abstract class BPUBundle extends Bundle with HasBPUParams


class RAS (implicit val conf: Config) extends HasBPUParams {
  def push(addr: UInt): Unit = {
    when (count < nRas.U) { count := count + 1.U }
    val nextTop = Mux(top < (nRas-1).U, top+1.U, 0.U)
    stack(nextTop) := addr
    top := nextTop
  }
  def peek: UInt = stack(top)
  def pop(): Unit = when (!isEmpty) {
    count := count - 1.U
    top := Mux( top > 0.U, top-1.U, (nRas-1).U)
  }
  def isEmpty: Bool = count === 0.U
  def clear(): Unit = count := 0.U

  val count =   RegInit(0.U(log2Up(nRas+1).W))
  val top   =   RegInit(0.U(log2Up(nRas).W))
  val stack =   Reg(Vec(nRas, UInt(conf.xprlen.W)))
}

class BTBUpdateReq (implicit val conf: Config) extends BPUBundle {
  val valid         = Output(Bool())
  val addr          = Output(UInt(conf.xprlen.W))
  val target        = Output(UInt(conf.xprlen.W))
  val taken         = Output(Bool())
  val is_miss       = Output(Bool())
  val redirect_type = Output(UInt(RD_JAL.getWidth.W))
}

class RASUpdateReq (implicit val conf: Config) extends BPUBundle {
  val valid         = Output(Bool())
  val addr          = Output(UInt(conf.xprlen.W))
  val target        = Output(UInt(conf.xprlen.W))
  val taken         = Output(Bool())
  val is_miss       = Output(Bool())
  val redirect_type = Output(UInt(RD_JAL.getWidth.W))
}


class BPUReq (implicit val conf: Config) extends BPUBundle {
  val addr = Input(UInt(conf.xprlen.W))
}

class BPUResp (implicit val conf: Config) extends BPUBundle {
  val valid     = Output(Bool())
  val target    = Output(UInt(conf.xprlen.W))
  val ras_valid = Output(Bool())
  val brIdx     = Output(UInt(groupSize.W))
  val pc        = Output(UInt(conf.xprlen.W))
  //   val brIdx      = Output(UInt(log2Up(nBTBEntries).W)) 
}

class BPUIO (implicit val conf: Config) extends BPUBundle {
  val req       = Flipped(Decoupled(new BPUReq))
  val resp      = Decoupled(new BPUResp)
  val ras_req   = Flipped((new RASUpdateReq))
  val btb_req   = Flipped((new BTBUpdateReq))
  val flush     = Input(Bool())
}

class BPU (implicit val conf: Config) extends BPUModule { 

    val io = IO(new BPUIO)
    // io := DontCare
    io.flush := DontCare
    
    val btb =  (0 until 2) map { i =>
      val bank = Module(new SRAMTemplate(BtbEntry(), line=nBTBEntries, ways=1))
      bank.io := DontCare
      bank
    }
    val btbRead = Wire(Vec(groupSize, BtbEntry()))
    // val replacer = new RandomReplacement(nBTBWays,nBTBEntries)

    val req_reg = RegEnable(io.req.bits, io.req.fire)
    val req_valid_reg = RegNext(io.req.valid)
    val addr_reg = req_reg.addr
    io.req.ready := btb.map{ x => x.io.r.req.ready}.reduce(_ && _)

    (0 until groupSize).map{ i =>
        btb(i).io.r.req.valid := io.req.valid && i.U === getGroupOffset(io.req.bits.addr)
        btb(i).io.r.req.bits.index := getIdx(io.req.bits.addr) }
    (0 until groupSize).map{ i => 
        btb(i).io.r.resp.ready := true.B
        btbRead(i) := btb(i).io.r.resp.bits.data(0)} 
    val btbHitVec = btbRead.map{ x => x.valid && getTag(addr_reg) === x.tag && req_valid_reg}
    // val btbHit = btbHitVec.andR


    val pht = List.fill(groupSize)(Reg(Vec(nBTBEntries, UInt(counterBits.W))))
    val pht_taken = (0 until groupSize).map{ i =>
        // 10 weak_taken, 11 strong_taken
        val taken = pht(i)(getIdx(addr_reg))(1) 
        taken
    }



    // BTB update 
    val btbWrite = WireInit(0.U.asTypeOf(BtbEntry()))
    val btb_req = io.btb_req
    val btb_req_reg = RegNext(btb_req)
    when(io.btb_req.valid){
        btbWrite.tag              := getTag(btb_req.addr)
        btbWrite.target           := btb_req.target
        btbWrite.valid            := btb_req.taken
        btbWrite.redirect_type    := btb_req.redirect_type 
    } 

    (0 until groupSize).map{ i =>
        btb(i).io.w.req.valid := io.btb_req.valid && io.btb_req.is_miss
                                 i.U === getGroupOffset(io.btb_req.addr)
        btb(i).io.w.req.bits.index    := getIdx(io.btb_req.addr)
        btb(i).io.w.req.bits.data     := btbWrite
        btb(i).io.w.req.bits.waymask  := 1.U
    }

    val getPht = Wire(Vec(groupSize, UInt(counterBits.W)))
    (0 until groupSize).map{ i =>
        getPht(i) := pht(i)(getIdx(io.req.bits.addr))
    }
    // all br,jal,jarl are redirect type && btb_req.redirect_type === RD_BR
    when(btb_req.valid ){
      val taken = btb_req.taken
      val newPht = Wire(Vec(groupSize, UInt(counterBits.W)))
      val should_update = getPht.map(x => (!taken && x =/= 0.U) || (taken && x =/= 3.U)).reduce(_ || _)
      (0 until groupSize).map{i => newPht(i) := Mux(taken, getPht(i) + 1.U, getPht(i) - 1.U)} 
      when(should_update){
        (0 until groupSize).map{ i =>
          pht(i)(getIdx(io.btb_req.addr)) := newPht(i)
        }
      }
    }
    
    val ras_target = WireInit(0.U(conf.xprlen.W))
    val isRASVec = WireInit(VecInit(Seq.fill(groupSize)(false.B)))
    if(nRas > 0){
        val ras = new RAS
        val isRASVec = btbRead.map(
                x => x.valid && getTag(addr_reg) === x.tag &&
                x.redirect_type === RD_RET )
        val isRas = isRASVec.reduce(_ || _)
        when(!ras.isEmpty && isRas){
          ras_target := ras.peek
        }
        when(btb_req.redirect_type === RD_JAL){
          ras.push(btb_req.addr + 4.U)      // support rvc should modify  
        }
        when (io.ras_req.valid && io.ras_req.redirect_type === RD_RET && !ras.isEmpty ){
          ras.pop()
        }
    }

    def getGroupMask(addr: UInt) = {
      val baseMask = ((1 << groupSize) - 1).U(groupSize.W)
      (baseMask << getGroupOffset(addr))(groupSize-1,0)
    }
    val inst_valid = getGroupMask(addr_reg)

    val target = Wire(Vec(groupSize, UInt(conf.xprlen.W)))
    val brIdx = Wire(Vec(groupSize,Bool()))
    (0 until groupSize).map{ i => target(i) := Mux(isRASVec(i), ras_target, btbRead(i).target)}
    (0 until groupSize).map{ i => brIdx(i)  := btbHitVec(i) && pht_taken(i) && inst_valid(i).asBool }
    io.resp.bits.target     := Mux1H(brIdx,target)
    io.resp.bits.ras_valid  := isRASVec.reduce(_ || _)
    io.resp.bits.brIdx      := brIdx.asUInt
    io.resp.valid           := brIdx.asUInt.orR 
    io.resp.bits.valid      := brIdx.asUInt.orR 
    io.resp.bits.pc         := addr_reg
    // Debug(io.resp.valid,"BPU: brIdx(0) %x brIdx(1) target %x pc %x ",brIdx(0),brIdx(1),io.resp.bits.target,addr_reg   )
}