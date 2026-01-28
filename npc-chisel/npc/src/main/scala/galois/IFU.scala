package npc.galois

import chisel3._
import chisel3.util._
import npc.common._
import npc._
import npc.galois.Constants._
import npc.common.UtilMethods.{ResultHoldBypass}

class IFUDebugPort(implicit val conf: Config)   extends Bundle() {
  val pc             = Output(UInt(conf.xprlen.W))
  val pc_next        = Output(UInt(conf.xprlen.W))
}

class IFUInstOut (implicit val conf: Config) extends OOOBundle {
  val pc              = Output(UInt(conf.xprlen.W))
  val inst            = Output(UInt(conf.xlen.W))
}
class IFUPipeIO(implicit val conf: Config) extends Bundle {
  val instA           = new IFUInstOut
  val instB           = new IFUInstOut
  val bpu_resp        = Output(new BPUResp)
  val exception       = Output(UInt(EXC_NORMAL.getWidth.W))
}

class InstFetchIo(implicit val conf: Config) extends CacheBundle {
  val ifu_dec           = new DecoupledIO(new IFUPipeIO())
  val retireA           = Flipped(new InstCtrlBlock)
  val redirect          = Input(Bool())
  val debug             = Output(new IFUDebugPort)
  val axi_bus           = new AXI4Bus()
}


class InstFetch(implicit conf: Config) extends Module {

  val io = IO(
    new InstFetchIo()
  )
  io := DontCare
  // Instruction Fetch
  val bpu         = Module(new BPU())
  val cache       = ICache()

  val bpu_valid   = bpu.io.resp.valid
  val bpu_target  = bpu.io.resp.bits.target
  val bpu_brIdx   = bpu.io.resp.bits.brIdx

  val pc_reg      = RegInit(conf.START_ADDR)
  val pc_next     = Wire(UInt(conf.xprlen.W))
  val pc_plus4    = (pc_reg + 4.asUInt(conf.xprlen.W))
  val pc_plus8    = (pc_reg + 8.asUInt(conf.xprlen.W))
  val unalign     = pc_reg(2) =/= 0.U
  when(cache.io.req.fire || io.redirect) {
      pc_reg := pc_next
  }.otherwise {
      pc_reg := pc_reg
  }

  // PC Register
  pc_next := Mux(bpu_valid,                         bpu_target,
              Mux(io.retireA.pc_sel  === PC_4,     Mux(unalign,pc_plus4,pc_plus8),   
                                                          io.retireA.target))
   // for a fencei, refetch the pc (assuming no branch, and no exception)
   val fencei = io.retireA.br_ctrl.fencei
   when ( fencei && io.retireA.pc_sel === PC_4 && !io.retireA.pc_sel =/= PC_EXC)
   {
      pc_next := pc_reg
   }
  dontTouch(pc_next)
  val btb_req = Wire(new BTBUpdateReq)
  val ras_req = Wire(new RASUpdateReq)
  val bju_out = io.retireA.bju_out
  val br_ctrl = io.retireA.br_ctrl
  btb_req.valid            := io.retireA.valid && br_ctrl.br_type =/= BR_N 
  btb_req.addr             := io.retireA.pc
  btb_req.target           := io.retireA.target
  btb_req.taken            := bju_out.taken
  btb_req.is_miss          := bju_out.predict_wrong
  btb_req.redirect_type    := bju_out.redirect_type 
  
  ras_req.valid            := io.retireA.valid && bju_out.redirect_type === RD_RET 
  ras_req.addr             := io.retireA.pc
  ras_req.target           := io.retireA.target
  ras_req.taken            := bju_out.taken
  ras_req.is_miss          := bju_out.predict_wrong
  ras_req.redirect_type    := bju_out.redirect_type

  // predict 1 cycle early and bpu need 1 cycle to predict the next pc 
  bpu.io := DontCare
  bpu.io.btb_req        <> btb_req
  bpu.io.ras_req        <> ras_req
  bpu.io.flush          := io.redirect
  bpu.io.req.valid      := cache.io.req.fire
  bpu.io.req.bits.addr  := pc_next

  // NOTE: when if_kill, should not take the old pc value
  cache.io := DontCare
  cache.io.req.valid            := io.ifu_dec.ready && !io.redirect
  cache.io.req.bits.addr        := pc_reg
  cache.io.resp.ready           := io.ifu_dec.ready
  cache.io.fencei               := io.retireA.br_ctrl.fencei
  cache.io.stop                 := io.redirect
  cache.io.axi_bus              <> io.axi_bus
  cache.io.debug                := DontCare
  cache.io.req.bits.bpu_resp    := bpu.io.resp.bits

  val cache_resp_valid = cache.io.resp.valid && !io.redirect
  val cache_resp_pc = cache.io.resp.bits.pc
  val cache_resp_data = cache.io.resp.bits.data
  val cache_pc_unalign = cache_resp_pc(2) =/= 0.U
  val bubbles = VecInit(Seq.fill(cache.blockRows)(BUBBLE))
  val can_flushed_insts = Mux(io.redirect,bubbles,cache_resp_data)
  val resp_instA = Mux(cache_pc_unalign, can_flushed_insts(1), can_flushed_insts(0))
  val resp_instB = Mux(cache_pc_unalign, BUBBLE, can_flushed_insts(1))

  val instA = ResultHoldBypass(resp_instA, cache_resp_valid || io.redirect)
  val instB = ResultHoldBypass(resp_instB, cache_resp_valid || io.redirect)
  val pcA   = cache_resp_pc
  val pcB   = Mux(cache_pc_unalign, 0.U, cache_resp_pc + 4.asUInt(conf.xprlen.W))
  
  // bpu prediction is based on block
  io.ifu_dec.valid :=   Mux(io.redirect , true.B,cache_resp_valid )
  io.ifu_dec.bits.instA.inst  := instA
  io.ifu_dec.bits.instA.pc    := pcA
  io.ifu_dec.bits.instB.inst  := instB
  io.ifu_dec.bits.instB.pc    := pcB
  io.ifu_dec.bits.exception   := cache.io.exception
  io.ifu_dec.bits.bpu_resp    <>  cache.io.resp.bits.bpu_resp


  ////////// debug
  io.debug.pc       := pc_reg
  io.debug.pc_next  := pc_next
  // val instFetchCount = RegInit(0.U(conf.perfCountBits.W))
  // when(cache.io.resp.valid) {
  //   instFetchCount := instFetchCount + 1.U
  // }
  // io.debug.valid := cache.io.resp.valid
  // io.debug.instFetchCount := instFetchCount
  
  ////////// end of debug 
}