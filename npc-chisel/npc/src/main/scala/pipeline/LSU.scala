
package npc.pipeline

import chisel3._
import chisel3.util._

import npc._
import npc.common._
import npc.pipeline.Constants._

import javax.xml.transform.OutputKeys
import npc.common.UtilMethods._

class LSUPipeIO(implicit val conf: Config) extends Bundle() {
    val wbaddr          = Output(UInt(5.W))
    val data            = Output(UInt(conf.xprlen.W))
    val pc              = Output(UInt(conf.xprlen.W))
    val pc_valid        = Output(Bool())
    val mem_resp_valid  = Output(Bool())
    val ebreak          = Output(Bool())
    val ctrl_rf_wen     = Output(Bool())
    val debug           = Output(new LSUDebugPort)
}

class CtlToLSUlIO (implicit val conf: Config) extends Bundle() {
    val mem_exception = Output(Bool())
}

class LSUTOCtlIO (implicit val conf: Config) extends Bundle() {
    val ctrl_mem_val    = Output(Bool())
    val alu_out         = Output(UInt(conf.xlen.W))
    val wbaddr          = Output(UInt(5.W))
    val wbdata          = Output(UInt(conf.xlen.W))
    val ctrl_rf_wen     = Output(Bool())
    val inst_is_load    = Output(Bool())
    val mem_exception   = Output(Bool())
    val csr_eret        = Output(Bool())
}


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

class CSRFiles(implicit val conf: Config) extends Module {
    val io = IO(new Bundle{
        val csr_inst            = Input(UInt(12.W))
        val csr_cmd             = Input(UInt(CSR.N.getWidth.W))
        val pc                  = Input(UInt(conf.xprlen.W))
        val alu_out             = Input(UInt(conf.xlen.W))
        val exception           = Input(UInt(EXC_NORMAL.getWidth.W))
        val exception_target    = Output(UInt(conf.xprlen.W))
        val rdata               = Output(UInt(conf.xlen.W))
        val ebreak              = Output(Bool())
        val eret                = Output(Bool())
    }) 
    // Control Status Registers
    val csr = Module(new CSRFile())
    csr.io := DontCare
    csr.io.decode.csr   := io.csr_inst
    csr.io.rw.cmd       := io.csr_cmd
    csr.io.rw.wdata     := io.alu_out
    csr.io.exception := io.exception
    csr.io.pc           := io.pc
    io.exception_target := csr.io.evec
    io.rdata            := csr.io.rw.rdata    
    io.ebreak := csr.io.insn_break
    io.eret := csr.io.eret

}


class LSUImplIO(implicit val conf: Config) extends CacheBundle {
    val exe_mem             = Flipped(new DecoupledIO(new EXEPipeIO()))
    val mem_wb              = new DecoupledIO(new LSUPipeIO)
    val debug               = new LSUDebugPort
    val exception_target    = Output(UInt(conf.xprlen.W))
    val to_ctl              = new LSUTOCtlIO
    val axi_bus             = new AXI4Bus()
    val clintIO = Flipped(  new Bundle{
            val dr      =   new AXIRport(conf.xprlen, conf.xlen)
            // val dw      =   new AXIWport(conf.xprlen, conf.xlen)
        })
}

class LSUImpl(implicit val conf: Config) extends Module with HasMMIOParams{
    val io = IO(new LSUImplIO())
    io := DontCare
    
    val axi_arb = Module(new RRArbiter(new AXI4Req(),2))
    val dcache  = Module(new L1Cache())
    val s_idle :: s_mmio_req :: s_clint_req :: s_dcache_req :: Nil = Enum(4) 
    val state = RegInit(s_idle)
    val mmio_axi_bus = Wire(new AXI4Bus())
    mmio_axi_bus.resp   <> io.axi_bus.resp
    axi_arb.io.in(0)    <> dcache.io.axi_bus.req
    axi_arb.io.in(1)    <> mmio_axi_bus.req
    axi_arb.io.out      <> io.axi_bus.req
    dcache.io.axi_bus.resp <> io.axi_bus.resp


    val exception = Wire(UInt(EXC_NORMAL.getWidth.W))
    val addr = io.exe_mem.bits.alu_out
    val mem_en = io.exe_mem.bits.ctrl_mem_val
    val in_clint        = addr >= CLINT_BASE && addr < (CLINT_BASE + CLINT_SIZE)
    val csr_files       = Module(new CSRFiles)
    val is_clint_req    = in_clint && mem_en
    val is_mmio_req     = mem_en && isMMIO(addr)
    val is_dcache_req    = mem_en && !is_mmio_req && !in_clint

    mmio_axi_bus.req.valid := is_mmio_req && state === s_idle
    switch(state){ 
        is(s_idle) {
            when(is_mmio_req &&  mmio_axi_bus.req.ready){
                state :=s_mmio_req
            } .elsewhen(is_clint_req ){
                state := s_clint_req
            } .elsewhen(is_dcache_req){
                state := s_dcache_req
            }
        }
        is(s_mmio_req) {
            when(io.axi_bus.resp.valid){
                state := s_idle
            }
        }
        is(s_clint_req) {
            when(io.clintIO.dr.ready){
                state := s_idle
            }
        }
        is(s_dcache_req) {
            when(dcache.io.resp.valid){
                state := s_idle
            }
        }
    }

    csr_files.io.pc         := io.exe_mem.bits.pc   
    csr_files.io.csr_inst   := io.exe_mem.bits.csr_inst
    csr_files.io.csr_cmd    := io.exe_mem.bits.ctrl_csr_cmd
    csr_files.io.alu_out    := io.exe_mem.bits.alu_out
    csr_files.io.exception  := exception

    io.exception_target     := csr_files.io.exception_target    
    
    // lsu should support mis-aligned access? or should based on slave type? 
    // val mis_aligned = Mux(mem_en && addr (1,0) =/= 0.U, true.B, false.B) 
    /////////// Write Port
    val req_typ     = io.exe_mem.bits.ctrl_mem_typ
    val wmask       =  Mux(req_typ === MT_B,1.U << addr(1,0),
                               Mux(req_typ === MT_H,3.U << addr(1,0),15.U))
    val wdata       = io.exe_mem.bits.rs2_data << (addr(1,0) << 3)
    val is_store    = (io.exe_mem.bits.ctrl_mem_fcn === M_XWR) && mem_en
    val is_read     = (io.exe_mem.bits.ctrl_mem_fcn === M_XRD) && mem_en

    val dcache_resp_data = WireInit(0.U)
    dcache.io.retire_store.valid     := true.B
    dcache.io.retire_store.bits.addr := addr
    dcache.io.req.valid              := is_dcache_req
    dcache.io.req.bits.addr          := addr
    dcache.io.req.bits.wdata         := wdata
    dcache.io.req.bits.wmask         := wmask
    dcache.io.req.bits.store         := is_store
    dcache.io.stall                  := false.B
    dcache.io.resp.ready             := true.B
    when(dcache.io.resp.valid){ 
        dcache_resp_data := dcache.io.resp.bits.data
    }
    
    val mmio_resp_data = WireInit(0.U)
    // axi4lite_mem.io.req.waddr := addr
    when(is_store ){
        mmio_axi_bus.req.bits.data  := wdata
        mmio_axi_bus.req.bits.mask  := wmask
    } .otherwise{
        mmio_axi_bus.req.bits.data  := 0.U
        mmio_axi_bus.req.bits.mask  := 0.U
    }
    when(mmio_axi_bus.resp.valid){
        mmio_resp_data := mmio_axi_bus.resp.bits.data
    }
    mmio_axi_bus.req.bits.raddr := addr
    mmio_axi_bus.req.bits.waddr := addr
    mmio_axi_bus.req.bits.ren   := (io.exe_mem.bits.ctrl_mem_fcn === M_XRD) && mem_en
    mmio_axi_bus.req.bits.wen   := (io.exe_mem.bits.ctrl_mem_fcn === M_XWR) && mem_en
    mmio_axi_bus.req.bits.burst := BURST_FIXED
    mmio_axi_bus.req.bits.burstlen := 0.U // single transfer
    mmio_axi_bus.req.bits.typ   := io.exe_mem.bits.ctrl_mem_typ
    mmio_axi_bus.resp.ready := true.B

    // read 
    // lsu should support mis-aligned access? or should based on slave type? 
    // val mis_aligned = Mux(mem_en && addr (1,0) =/= 0.U, true.B, false.B)
    val to_axi_fire = mmio_axi_bus.req.fire.asBool || dcache.io.req.fire
    val req_typ_reg = ResultHoldBypass(io.exe_mem.bits.ctrl_mem_typ, to_axi_fire)
    val req_addr    = ResultHoldBypass(addr,to_axi_fire)
    
    val axi_resp_data = Mux(is_mmio_req,mmio_resp_data,Mux(is_dcache_req,dcache_resp_data,0.U)) 
    val aligned_resp_data = axi_resp_data >> (req_addr(1,0)<<3)
    val resp_data = MuxCase(aligned_resp_data,Seq(
      (req_typ_reg === MT_B) -> Cat(Fill(24,aligned_resp_data(7)),aligned_resp_data(7,0)),
      (req_typ_reg === MT_H) -> Cat(Fill(16,aligned_resp_data(15)),aligned_resp_data(15,0)),
      (req_typ_reg === MT_BU) -> Cat(Fill(24,0.U),aligned_resp_data(7,0)),
      (req_typ_reg === MT_HU) -> Cat(Fill(16,0.U),aligned_resp_data(15,0))
    ))

 
    when (is_clint_req ){
        when (io.exe_mem.bits.ctrl_mem_fcn === M_XRD){
            io.clintIO.dr.en := true.B
            io.clintIO.dr.addr := addr
        } .otherwise{
            io.clintIO.dr.en := false.B
        }
    }
    val mmio_resp_valid = io.axi_bus.resp.valid && state === s_mmio_req
    val mmio_exception  = io.axi_bus.resp.bits.resp

    val mem_port_resp_valid = mmio_resp_valid || dcache.io.resp.valid 
    val mem_resp_valid  = Mux(in_clint, io.clintIO.dr.ready,    mem_port_resp_valid)
    val mem_exception   = Mux(in_clint, 0.U,                    (io.axi_bus.resp.bits.resp))
    val mem_data        = Mux(in_clint, io.clintIO.dr.data ,    (resp_data ))
    val mem_ready = (!io.exe_mem.bits.ctrl_mem_val)  || (io.exe_mem.bits.ctrl_mem_val && mem_resp_valid)
    val ready = mem_ready
    io.exe_mem.ready := io.mem_wb.ready && ready

    // WB Mux
    val wbdata = MuxCase(io.exe_mem.bits.alu_out, Array(
                  (io.exe_mem.bits.ctrl_wb_sel === WB_ALU) -> io.exe_mem.bits.alu_out,
                  (io.exe_mem.bits.ctrl_wb_sel === WB_PC4) -> io.exe_mem.bits.alu_out,
                  (io.exe_mem.bits.ctrl_wb_sel === WB_MEM) -> mem_data,
                  (io.exe_mem.bits.ctrl_wb_sel === WB_CSR) -> csr_files.io.rdata
                  ))
    exception := Mux(mem_en && mem_exception =/= 0.U , 
            Mux(io.exe_mem.bits.ctrl_mem_typ === M_XRD, EXC_LOAD_ACCESS_FAULT, 
            Mux(io.exe_mem.bits.ctrl_mem_typ === M_XWR, EXC_STORE_ACCESS_FAULT,EXC_NORMAL)), io.exe_mem.bits.exception )
    io.mem_wb.valid                 := (!mem_en || (mem_en && mem_resp_valid))
    io.mem_wb.bits.data             := wbdata
    io.mem_wb.bits.wbaddr           := io.exe_mem.bits.wbaddr
    io.mem_wb.bits.ebreak           := csr_files.io.ebreak
    io.mem_wb.bits.pc               := io.exe_mem.bits.pc
    io.mem_wb.bits.ctrl_rf_wen      := io.exe_mem.bits.ctrl_rf_wen
    // io.mem_wb.bits.inst             := io.exe_mem.bits.inst
    io.mem_wb.bits.pc_valid         := io.exe_mem.bits.pc_valid 
    io.mem_wb.bits.mem_resp_valid   := mem_resp_valid
    io.mem_wb.bits.debug            := io.debug
    
    io.to_ctl.ctrl_mem_val      := mem_en
    io.to_ctl.wbdata            := wbdata
    io.to_ctl.wbaddr            := io.exe_mem.bits.wbaddr
    io.to_ctl.ctrl_rf_wen       := io.exe_mem.bits.ctrl_rf_wen
    io.to_ctl.alu_out           := io.exe_mem.bits.alu_out
    io.to_ctl.inst_is_load      := io.exe_mem.bits.ctrl_mem_val && (io.exe_mem.bits.ctrl_mem_fcn === M_XRD)
    io.to_ctl.csr_eret          := csr_files.io.eret
    io.to_ctl.mem_exception     := exception

    /////////// Debug Port
    val storeCnt        = RegInit(0.U(conf.perfCountBits.W))
    val loadCnt         = RegInit(0.U(conf.perfCountBits.W))
    io.debug.mem_en     := mem_en
    io.debug.addr       := addr
    io.debug.fcn        := io.exe_mem.bits.ctrl_mem_fcn
    io.debug.wdata      := io.exe_mem.bits.rs2_data 
    io.debug.rdata      := mem_data
    io.debug.valid      := mem_resp_valid
    io.debug.typ        := io.exe_mem.bits.ctrl_mem_typ
    when(mem_port_resp_valid && mem_en) {
      when(io.exe_mem.bits.ctrl_mem_fcn === M_XWR) {
        storeCnt := storeCnt + 1.U
      }.otherwise {
        loadCnt := loadCnt + 1.U
      }
    }
    io.debug.loadCount  := loadCnt
    io.debug.storeCount := storeCnt
    /////////// Debug Port
}

