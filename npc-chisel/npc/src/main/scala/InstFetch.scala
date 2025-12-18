package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._
import npc.common.UtilMethods.{ResultHoldBypass}

class IFUDebugPort(implicit val conf: Config)   extends Bundle() {
  val valid           = Output(Bool())
  val instFetchCount  = Output(UInt(conf.perfCountBits.W))
  val icache          = new ICacheDebugPort
}

class IFUPipeIO(implicit val conf: Config) extends Bundle {
  val pc              = Output(UInt(conf.xprlen.W))
  val inst            = Output(UInt(conf.xprlen.W))
  val pc_valid        = Output(Bool())
  val bpu_resp        = Output(new BPUResp)
  val exception       = Output(UInt(EXC_NORMAL.getWidth.W))
}

class InstFetchIo(implicit val conf: Config) extends CacheBundle {
  val ctl               = new CtrlSignalIO
  val exu_in            = Flipped(new EXUToIFUOut)
  val ifu_dec           = new DecoupledIO(new IFUPipeIO())
  val exception_target  = Input(UInt(conf.xprlen.W))
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

  val bpu_valid   = Reg(Bool())
  val bpu_target  = Reg(UInt(conf.xprlen.W))
  val bpu_brIdx   = Reg(UInt(bpu.groupBits.W))

  val pc_reg      = RegInit(conf.START_ADDR)
  val pc_next     = Wire(UInt(conf.xprlen.W))
  val pc_plus4    = (pc_reg + 4.asUInt(conf.xprlen.W))
  val should_kill = io.ctl.if_kill || io.ctl.pipeline_kill

  // val if_valid = Mux(cache.io.resp.valid,cache.io.resp.valid,
  //           RegEnable(cache.io.resp.valid && !io.ifu_dec.ready,false.B,cache.io.resp.valid || io.ifu_dec.ready))
  when(cache.io.req.fire || should_kill) {
      pc_reg := pc_next
  }.otherwise {
      pc_reg := pc_reg
  }

  // PC Register
  pc_next :=  Mux(io.ctl.exe_pc_sel     === PC_4,         pc_plus4,
                 Mux(io.ctl.exe_pc_sel  === PC_BRJMP,  io.exu_in.exe_brjmp_target,
                 Mux(io.ctl.exe_pc_sel  === PC_JALR,   io.exu_in.exe_jump_reg_target,
                 Mux(bpu_valid,                         bpu_target,
                 /*Mux(io.ctl.pc_sel === PC_EXC*/ io.exception_target))))

   // for a fencei, refetch the pc (assuming no branch, and no exception)
   when (io.ctl.fencei && io.ctl.exe_pc_sel === PC_4 && !io.ctl.pipeline_kill)
   {
      pc_next := pc_reg
   }


  when(bpu.io.resp.valid) {
    bpu_valid     := bpu.io.resp.valid
    bpu_target    := bpu.io.resp.bits.target
    bpu_brIdx     := bpu.io.resp.bits.brIdx
  }

  when(cache.io.req.fire || should_kill){
    bpu_valid     := false.B
    bpu_target      := 0.U
    bpu_brIdx     := 0.U
  }
  bpu.io := DontCare
  bpu.io.btb_req        <> io.exu_in.btb_req
  bpu.io.ras_req        <> io.exu_in.ras_req
  bpu.io.flush          := should_kill
  bpu.io.req.valid      := cache.io.req.fire
  bpu.io.req.bits.addr  := pc_next

  // NOTE: when if_kill, should not take the old pc value
  cache.io := DontCare
  cache.io.req.valid      := io.ifu_dec.ready && !should_kill
  cache.io.req.bits.addr  := pc_reg
  cache.io.resp.ready     := io.ifu_dec.ready
  cache.io.fencei         := io.ctl.fencei
  cache.io.stop           := should_kill
  cache.io.axi_bus        <> io.axi_bus
  cache.io.debug          <> io.debug.icache 

  // cache.io.port       <> io.port
  // NOTE: if_kill should clean inst, in ifu_dec reg
  val cache_resp_valid = cache.io.resp.valid && !should_kill
  val cache_resp_pc = cache.io.resp.bits.pc
  val cache_inst = cache.io.resp.bits.data
  val can_flushed_inst = Mux(should_kill,BUBBLE,cache_inst)
  val inst = ResultHoldBypass(can_flushed_inst, cache_resp_valid || should_kill)
  io.ifu_dec.valid :=   Mux(should_kill , true.B,cache_resp_valid )
  io.ifu_dec.bits.inst := inst
  io.ifu_dec.bits.pc := cache_resp_pc
  io.ifu_dec.bits.pc_valid := Mux(should_kill || cache.io.exception =/= EXC_NORMAL, false.B, true.B)
  io.ifu_dec.bits.exception := cache.io.exception


  ////////// debug
  val instFetchCount = RegInit(0.U(conf.perfCountBits.W))
  when(cache.io.resp.valid) {
    instFetchCount := instFetchCount + 1.U
  }
  io.debug.valid := cache.io.resp.valid
  io.debug.instFetchCount := instFetchCount
  ////////// end of debug 
}