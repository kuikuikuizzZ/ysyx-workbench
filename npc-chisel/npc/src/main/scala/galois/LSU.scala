
package npc.galois

import chisel3._
import chisel3.util._

import npc.common._
import npc._
import npc.galois.Constants._
import javax.xml.transform.OutputKeys
import npc.common.UtilMethods._





class LSUDebugPort(implicit val conf: Config) extends Bundle {
    val mem_en      = Output(Bool())
    val fcn         = Output(Bool())
    val addr        = Output(UInt(conf.xprlen.W))
    val rdata       = Output(UInt(conf.xprlen.W))
    val wdata       = Output(UInt(conf.xprlen.W))
    val valid       = Output(Bool())
    val typ         = Output(UInt(2.W))
    val storeCount  = Output(UInt(conf.perfCountBits.W))
    val loadCount   = Output(UInt(conf.perfCountBits.W))
}


class LSUImplIO(implicit val conf: Config) extends CacheBundle {
    val exe_mem             = Flipped(new DecoupledIO(new InstCtrlBlock))
    val retire_store        = Flipped(new DecoupledIO(new InstCtrlBlock))
    val cmtE                = Output(new InstCtrlBlock)
    val cmtF                = Output(new InstCtrlBlock)

    val forward_load        = Output(new InstCtrlBlock)
    val forward_store       = Input(new InstCtrlBlock)

    val debug               = new LSUDebugPort
    val redirect            = Input(Bool())
    val axi_bus             = new AXI4Bus()
    val clintIO = Flipped(  new Bundle{
            val dr      =   new AXIRport(conf.xprlen, conf.xlen)
            // val dw      =   new AXIWport(conf.xprlen, conf.xlen)
        })
}


class LSUImpl(implicit val conf: Config) extends OOOModule {
    val io = IO(new LSUImplIO())
    io := DontCare
    io.debug := DontCare
    val inst = io.exe_mem.bits
    val axi_arb      = Module(new RRArbiter(new AXI4Req(),2))
    val load_unit    = Module(new LoadUnit())
    val store_unit   = Module(new StoreUnit())
    val retire_is_store = io.retire_store.bits.mem_ctrl.mem_val && io.retire_store.bits.mem_ctrl.mem_fcn === M_XWR
    val queue        = Module(new Queue(new InstCtrlBlock, LSQ_SIZE,pipe = true,flow=true, hasFlush = true))

    axi_arb.io.out   <> io.axi_bus.req
    axi_arb.io.in(0) <> store_unit.io.axi_bus.req 
    axi_arb.io.in(1) <> load_unit.io.axi_bus.req   

    val deq_is_load     = queue.io.deq.bits.mem_ctrl.mem_val && queue.io.deq.bits.mem_ctrl.mem_fcn === M_XRD
    val deq_is_store    = queue.io.deq.bits.mem_ctrl.mem_val && queue.io.deq.bits.mem_ctrl.mem_fcn === M_XWR
    val store_inst      = Mux(deq_is_store && queue.io.deq.valid, queue.io.deq.bits, 0.U.asTypeOf(new InstCtrlBlock))
    val load_inst       = Mux(deq_is_load && queue.io.deq.valid, queue.io.deq.bits, 0.U.asTypeOf(new InstCtrlBlock))
    val store_out       = InstCtrlBlock.copy(base=store_inst,finish= Some(true.B))
    io.exe_mem.ready            := queue.io.enq.ready
    queue.io.flush.get          := io.redirect
    queue.io.enq.bits           <> io.exe_mem.bits
    queue.io.enq.valid          := RegNext(io.exe_mem.valid)
    queue.io.deq.ready          := load_unit.io.in.ready || deq_is_store


    load_unit.io.in.bits        := load_inst
    load_unit.io.in.valid       := deq_is_load && queue.io.deq.valid
    load_unit.io.debug          := DontCare
    load_unit.io.axi_bus.resp   <> io.axi_bus.resp
    load_unit.io.clintIO        <> io.clintIO
    load_unit.io.forward_load   <> io.forward_load
    load_unit.io.forward_store  <> io.forward_store
    load_unit.io.redirect       := io.redirect
    load_unit.io.out.ready      := true.B
    load_unit.io.axi_bus.req.ready      := axi_arb.io.in(0).ready

    store_unit.io.axi_bus.req.ready      := axi_arb.io.in(1).ready
    store_unit.io.debug                  := DontCare
    // should add 1 cycle latency
    store_unit.io.in <> io.retire_store
    store_unit.io.axi_bus.resp  <> io.axi_bus.resp

    // store also should commit to ROB, but not executed
    io.cmtE                     := Mux(load_unit.io.out.valid, load_unit.io.out.bits, 0.U.asTypeOf(new InstCtrlBlock) )
    io.cmtF                     := Mux(store_inst.valid, store_out, 0.U.asTypeOf(new InstCtrlBlock))
    // store is execute after retire

    /////////// Debug Port
    // val storeCnt        = RegInit(0.U(conf.perfCountBits.W))
    // val loadCnt         = RegInit(0.U(conf.perfCountBits.W))
    // io.debug.mem_en     := mem_en
    // io.debug.addr       := addr
    // io.debug.fcn        := mem_ctrl.mem_fcn
    // io.debug.wdata      := io.in.bits.rs2_data 
    // io.debug.rdata      := mem_data
    // io.debug.valid      := mem_resp_valid
    // io.debug.typ        := mem_ctrl.mem_typ
    // when(io.axi_bus.resp.valid && mem_en) {
    //   when(mem_ctrl.mem_fcn === M_XWR) {
    //     storeCnt := storeCnt + 1.U
    //   }.otherwise {
    //     loadCnt := loadCnt + 1.U
    //   }
    // }
    // io.debug.loadCount  := loadCnt
    // io.debug.storeCount := storeCnt
    /////////// Debug Port
}

class LoadUnit (implicit val conf: Config) extends OOOModule { 
    val io = IO(new Bundle{
        val in = Flipped(new DecoupledIO(new InstCtrlBlock))
        val out = new DecoupledIO(new InstCtrlBlock)
        val axi_bus             = new AXI4Bus()
        val redirect            = Input(Bool())
        val forward_load        = Output(new InstCtrlBlock)
        val forward_store       = Input(new InstCtrlBlock)
        val clintIO = Flipped(  new Bundle{
            val dr      =   new AXIRport(conf.xprlen, conf.xlen)
        })
        val debug = new LSUDebugPort
    })
        
    val s_idle :: s_bus_req :: s_clint_req :: Nil = Enum(3) 
    val state = RegInit(s_idle)

    val exception   = Wire(UInt(EXC_NORMAL.getWidth.W))

    val addr            = io.in.bits.alu_out
    val inst            = Mux(io.in.valid, io.in.bits, 0.U.asTypeOf(new InstCtrlBlock))
    val inst_is_load    = inst.mem_ctrl.mem_val && inst.mem_ctrl.mem_fcn === M_XRD
    val mem_ctrl        = io.in.bits.mem_ctrl
    val mem_en          = mem_ctrl.mem_val

    val in_clint = addr >= CLINT_BASE && addr < (CLINT_BASE + CLINT_SIZE)
    val is_bus_req = mem_en && !in_clint
    val is_clint_req = in_clint && mem_en

    val ready = io.axi_bus.req.ready 
    io.in.ready := ready 
    io.axi_bus.req.valid := inst_is_load && !in_clint && state === s_idle
    switch(state){ 
        is(s_idle) {
            when(inst_is_load){
                when(is_bus_req &&  io.axi_bus.req.fire){
                    state :=s_bus_req
                } .elsewhen(is_clint_req ){
                    state := s_clint_req
                }
            }
        }
        is(s_bus_req) {
            when(io.axi_bus.resp.valid){
                state := s_idle
            }
        }
        is(s_clint_req) {
            when(io.clintIO.dr.ready){
                state := s_idle
            }
        }
    }

    val to_axi_fire = io.axi_bus.req.fire.asBool
    
    // lsu should support mis-aligned access? or should based on slave type? 
    // val mis_aligned = Mux(mem_en && addr (1,0) =/= 0.U, true.B, false.B) 
    /////////// Write Port
    val req_typ = mem_ctrl.mem_typ
    io.axi_bus.req.bits.burst := BURST_FIXED
    io.axi_bus.req.bits.burstlen := 0.U // single transfer
    io.axi_bus.req.bits.raddr := addr
    io.axi_bus.req.bits.ren   := inst_is_load && io.axi_bus.req.fire
    io.axi_bus.req.bits.waddr := addr
    io.axi_bus.req.bits.wen   := false.B
    io.axi_bus.req.bits.data    := 0.U
    io.axi_bus.req.bits.mask    := 0.U
    io.axi_bus.req.bits.typ     := MT_X
    io.axi_bus.resp.ready       := true.B
    // read 
    // lsu should support mis-aligned access? or should based on slave type? 
    // val mis_aligned = Mux(mem_en && addr (1,0) =/= 0.U, true.B, false.B)
    val inst_redirect   = Mux(io.redirect,0.U.asTypeOf(new InstCtrlBlock),io.in.bits)
    val req_typ_reg     = ResultHoldBypass(mem_ctrl.mem_typ, to_axi_fire)
    val req_addr        = ResultHoldBypass(io.axi_bus.req.bits.raddr,to_axi_fire)
    val reg_wb_ctrl     = ResultHoldBypass(io.in.bits.wb_ctrl, to_axi_fire) 
    val reg_inst        = ResultHoldBypass(inst_redirect, to_axi_fire || io.redirect)
    


    // forward load
    // forward store return and used in next cycle
    io.forward_load := Mux(io.in.fire && inst_is_load, io.in.bits, 0.U.asTypeOf(new InstCtrlBlock))
    // forward store should be flush by redirect or axi_bus.resp.valid
    val reg_forward_store = RegEnable(io.forward_store, io.forward_store.valid || io.redirect || io.axi_bus.resp.valid) 
    val w_typ = reg_forward_store.mem_ctrl.mem_typ
    val waddr = reg_forward_store.alu_out
    val wmask = Mux(reg_forward_store.valid, Mux(w_typ === MT_B,1.U << waddr(1,0),
                               Mux(w_typ === MT_H,3.U << waddr(1,0),15.U)), 
                               0.U)
    val byteMasks = VecInit(Seq.tabulate(4)(i => Fill(8, wmask(i))))
    val byteMask = Cat(byteMasks(3), byteMasks(2), byteMasks(1), byteMasks(0))
    val wdata = Mux(reg_forward_store.valid, reg_forward_store.rs2_data, 0.U)
     
    val d_data      = (io.axi_bus.resp.bits.data & ~byteMask) | (wdata & byteMask)
    
    val aligned_resp_data = d_data >> (req_addr(1,0)<<3)
    val resp_data = MuxCase(aligned_resp_data,Seq(
      (req_typ_reg === MT_B) -> Cat(Fill(24,aligned_resp_data(7)),aligned_resp_data(7,0)),
      (req_typ_reg === MT_H) -> Cat(Fill(16,aligned_resp_data(15)),aligned_resp_data(15,0)),
      (req_typ_reg === MT_BU) -> Cat(Fill(24,0.U),aligned_resp_data(7,0)),
      (req_typ_reg === MT_HU) -> Cat(Fill(16,0.U),aligned_resp_data(15,0))
    ))

 
    when (is_clint_req ){
        when (mem_ctrl.mem_fcn === M_XRD){
            io.clintIO.dr.en := true.B
            io.clintIO.dr.addr := addr
        } .otherwise{
            io.clintIO.dr.en := false.B
            io.clintIO.dr.addr := addr

        }
    }.otherwise{
        io.clintIO.dr.en := false.B
        io.clintIO.dr.addr := 0.U
    }

    val mem_port_resp_valid = io.axi_bus.resp.valid && state === s_bus_req 
    val mem_resp_valid  = Mux(in_clint, io.clintIO.dr.ready,    mem_port_resp_valid)
    val mem_exception   = Mux(in_clint, 0.U,                    (io.axi_bus.resp.bits.resp))
    val mem_data        = Wire(UInt(conf.xlen.W))
    mem_data            := Mux(in_clint, io.clintIO.dr.data ,    (resp_data ))
    dontTouch(mem_data)
    // WB Mux
    val wbdata = Mux(reg_wb_ctrl.wb_sel === WB_MEM, mem_data,reg_inst.alu_out)
                
    exception := Mux(mem_en && mem_exception =/= 0.U , 
            Mux(mem_ctrl.mem_typ === M_XRD, EXC_LOAD_ACCESS_FAULT, 
            Mux(mem_ctrl.mem_typ === M_XWR, EXC_STORE_ACCESS_FAULT,EXC_NORMAL)), EXC_NORMAL)
    val finish = mem_resp_valid 

    io.out.valid := finish                             
    val out_block = InstCtrlBlock.copy(base = (reg_inst),
                                 wb_data = Some(wbdata), finish= Some(finish),
                                 exception = Some(exception))
    io.out.bits     := out_block
    /////////// Debug Port
    val loadCnt         = RegInit(0.U(conf.perfCountBits.W))
    io.debug.mem_en     := mem_en
    io.debug.addr       := addr
    io.debug.fcn        := mem_ctrl.mem_fcn
    io.debug.wdata      := io.in.bits.rs2_data 
    io.debug.rdata      := mem_data
    io.debug.valid      := mem_resp_valid
    io.debug.typ        := mem_ctrl.mem_typ
    when(io.axi_bus.resp.valid && mem_en) {
      when(mem_ctrl.mem_fcn === M_XRD) {
        loadCnt := loadCnt + 1.U
      }
    }
    io.debug.loadCount  := loadCnt
    io.debug.storeCount := 0.U
    /////////// Debug Port                             
}

class StoreUnit (implicit val conf: Config) extends OOOModule { 
    val io = IO(new Bundle{
        val in          = Flipped(new DecoupledIO(Input(new InstCtrlBlock)))
        val axi_bus     = new AXI4Bus()
        val debug       = new LSUDebugPort
    })

    val s_idle :: s_bus_req :: Nil = Enum(2) 
    val state = RegInit(s_idle)
    val exception   = Wire(UInt(EXC_NORMAL.getWidth.W))
    
    val addr        = io.in.bits.alu_out
    val mem_ctrl    = io.in.bits.mem_ctrl
    val inst        = Mux(io.in.valid, io.in.bits, 0.U.asTypeOf(new InstCtrlBlock))
    val is_load     = inst.mem_ctrl.mem_val && inst.mem_ctrl.mem_fcn === M_XRD
    val is_store    = inst.mem_ctrl.mem_val && inst.mem_ctrl.mem_fcn === M_XWR 
    val mem_en      = mem_ctrl.mem_val
    val is_bus_req  = mem_en 

    io.axi_bus.req.valid := mem_en && state === s_idle && io.in.valid
    switch(state){ 
        is(s_idle) {
            when(is_store && is_bus_req &&  io.axi_bus.req.ready){
                state :=s_bus_req
            }
        }
        is(s_bus_req) {
            when(io.axi_bus.resp.valid){
                state := s_idle
            }
        }
    }

    val to_axi_fire = io.axi_bus.req.fire.asBool
    
    // lsu should support mis-aligned access? or should based on slave type? 
    // val mis_aligned = Mux(mem_en && addr (1,0) =/= 0.U, true.B, false.B) 
    /////////// Write Port
    val req_typ = mem_ctrl.mem_typ
    io.axi_bus.req.bits.burst := BURST_FIXED
    io.axi_bus.req.bits.burstlen := 0.U // single transfer
    io.axi_bus.req.bits.raddr := addr
    io.axi_bus.req.bits.ren   := false.B
    
    io.axi_bus.req.bits.waddr := addr
    io.axi_bus.req.bits.wen   := is_store   
    io.axi_bus.req.bits.typ   := req_typ
    io.axi_bus.resp.ready     := true.B
    when (is_store){
       // axi4lite_mem.io.req.waddr := addr
       io.axi_bus.req.bits.data  := io.in.bits.rs2_data << (addr(1,0) << 3)
       io.axi_bus.req.bits.mask  := Mux(req_typ === MT_B,1.U << addr(1,0),
                               Mux(req_typ === MT_H,3.U << addr(1,0),15.U))
    }.otherwise{
       io.axi_bus.req.bits.data  := 0.U
       io.axi_bus.req.bits.mask  := 0.U
    } 



    val mem_port_resp_valid = io.axi_bus.resp.valid && state === s_bus_req 
    val mem_resp_valid  =    mem_port_resp_valid
    val mem_exception   = io.axi_bus.resp.bits.resp
    val mem_ready       = (!mem_ctrl.mem_val) || (mem_ctrl.mem_val && mem_resp_valid)
    io.in.ready := io.axi_bus.req.ready || !is_store
    dontTouch(io.axi_bus.req.ready)
    // io.in.ready := io.axi_bus.req.ready 
    // WB Mux
                
    exception := Mux(mem_en && mem_exception =/= 0.U , 
            Mux(mem_ctrl.mem_typ === M_XRD, EXC_LOAD_ACCESS_FAULT, 
            Mux(mem_ctrl.mem_typ === M_XWR, EXC_STORE_ACCESS_FAULT,EXC_NORMAL)), EXC_NORMAL)
    
    /////////// Debug Port
    val storeCnt        = RegInit(0.U(conf.perfCountBits.W))
    io.debug.mem_en     := mem_en
    io.debug.addr       := addr
    io.debug.fcn        := mem_ctrl.mem_fcn
    io.debug.wdata      := io.in.bits.rs2_data 
    io.debug.rdata      := 0.U
    io.debug.valid      := mem_resp_valid
    io.debug.typ        := mem_ctrl.mem_typ
    when(io.axi_bus.resp.valid && mem_en) {
      when(mem_ctrl.mem_fcn === M_XWR) {
        storeCnt := storeCnt + 1.U
      }
    }
    io.debug.loadCount  := 0.U
    io.debug.storeCount := storeCnt 
}