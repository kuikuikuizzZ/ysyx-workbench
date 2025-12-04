
package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._
import javax.xml.transform.OutputKeys

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
class LSUIO(implicit val conf: Config) extends Bundle {
    val exe_mem             = Flipped(new DecoupledIO(new EXEPipeIO()))
    val mem_wb              = new DecoupledIO(new LSUPipeIO)
    val port                = new MemPortIo(conf.xprlen)
    val debug               = new LSUDebugPort
    val exception_target    = Output(UInt(conf.xprlen.W))
    val to_ctl             = new LSUTOCtlIO
    
    val clintIO = Flipped(  new Bundle{
            val dr      =   new AXIRport(conf.xprlen, conf.xlen)
            val dw      =   new AXIWport(conf.xprlen, conf.xlen)
        })
}
class LSU(implicit val conf: Config) extends Module {
    val io = IO(new LSUIO())
    io := DontCare
    val s_idle :: s_bus_req :: s_clint_req :: Nil = Enum(3) 
    val state = RegInit(s_idle)
    val exception = Wire(UInt(EXC_NORMAL.getWidth.W))
    val addr = io.exe_mem.bits.alu_out
    val mem_en = io.exe_mem.bits.ctrl_mem_val
    val in_clint = addr >= CLINT_BASE && addr < (CLINT_BASE + CLINT_SIZE)
    val csr_files = Module(new CSRFiles)
    val is_bus_req = mem_en && !in_clint
    val is_clint_req = in_clint && mem_en

    switch(state){ 
        is(s_idle) {
            when(is_bus_req &&  io.port.req.ready){
                io.port.req.valid    := mem_en
                io.port.req.bits.fcn := io.exe_mem.bits.ctrl_mem_fcn
                io.port.req.bits.typ := io.exe_mem.bits.ctrl_mem_typ
                io.port.req.bits.addr := addr
                io.port.req.bits.data := io.exe_mem.bits.rs2_data 
                io.port.req.bits.burstlen := 0.U
                io.port.req.bits.burst := BURST_FIXED
                state :=s_bus_req
            } .elsewhen(is_clint_req ){
                state := s_clint_req
                io.port.req.valid := false.B
            }
        }
        is(s_bus_req) {
            io.port.req.valid := false.B
            when(io.port.resp.valid){
                state := s_idle
            }
        }
        is(s_clint_req) {
            when(io.clintIO.dr.ready){
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
    when (is_clint_req ){
        when (io.exe_mem.bits.ctrl_mem_fcn === M_XRD){
            io.clintIO.dr.en := true.B
            io.clintIO.dr.addr := addr
        } .otherwise{
            io.clintIO.dr.en := false.B
        }
    }
    
    
    val mem_port_resp_valid = io.port.resp.valid && state === s_bus_req 
    val resp_data = io.port.resp.bits.data
    val mem_resp_valid  = Mux(in_clint, io.clintIO.dr.ready,    mem_port_resp_valid)
    val mem_exception   = Mux(in_clint, 0.U,                    (io.port.resp.bits.resp))
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
    when(io.port.resp.valid && mem_en) {
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

class LSUImplIO(implicit val conf: Config) extends CacheBundle {
    val exe_mem             = Flipped(new DecoupledIO(new EXEPipeIO()))
    val mem_wb              = new DecoupledIO(new LSUPipeIO)
    val debug               = new LSUDebugPort
    val exception_target    = Output(UInt(conf.xprlen.W))
    val to_ctl              = new LSUTOCtlIO
    val axi_bus             = new AXI4Bus()
    val clintIO = Flipped(  new Bundle{
            val dr      =   new AXIRport(conf.xprlen, conf.xlen)
            val dw      =   new AXIWport(conf.xprlen, conf.xlen)
        })
}

class LSUImpl(implicit val conf: Config) extends Module {
    val io = IO(new LSUImplIO())
    io := DontCare
    
    val s_idle :: s_bus_req :: s_clint_req :: Nil = Enum(3) 
    val state = RegInit(s_idle)
    val exception = Wire(UInt(EXC_NORMAL.getWidth.W))
    val addr = io.exe_mem.bits.alu_out
    val mem_en = io.exe_mem.bits.ctrl_mem_val
    val in_clint = addr >= CLINT_BASE && addr < (CLINT_BASE + CLINT_SIZE)
    val csr_files = Module(new CSRFiles)
    val is_bus_req = mem_en && !in_clint
    val is_clint_req = in_clint && mem_en

    io.axi_bus.req.valid := mem_en && !in_clint
    switch(state){ 
        is(s_idle) {
            when(is_bus_req &&  io.axi_bus.req.ready){
                state :=s_bus_req
            } .elsewhen(is_clint_req ){
                state := s_clint_req
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
    val req_fire = RegEnable(!to_axi_fire, false.B)
    csr_files.io.pc         := io.exe_mem.bits.pc   
    csr_files.io.csr_inst   := io.exe_mem.bits.csr_inst
    csr_files.io.csr_cmd    := io.exe_mem.bits.ctrl_csr_cmd
    csr_files.io.alu_out    := io.exe_mem.bits.alu_out
    csr_files.io.exception  := exception

    io.exception_target     := csr_files.io.exception_target    
    
    // lsu should support mis-aligned access? or should based on slave type? 
    // val mis_aligned = Mux(mem_en && addr (1,0) =/= 0.U, true.B, false.B) 
    /////////// Write Port
    val req_typ = io.exe_mem.bits.ctrl_mem_typ
    io.axi_bus.req.bits.raddr := addr
    io.axi_bus.req.bits.waddr := Cat(addr(31,2),0.asUInt(2.W))
    io.axi_bus.req.bits.ren   := (io.exe_mem.bits.ctrl_mem_fcn === M_XRD) && mem_en
    io.axi_bus.req.bits.wen   := (io.exe_mem.bits.ctrl_mem_fcn === M_XWR) && mem_en
    io.axi_bus.req.bits.burst := BURST_FIXED
    io.axi_bus.req.bits.burstlen := 0.U // single transfer
    when (mem_en && (io.exe_mem.bits.ctrl_mem_fcn === M_XWR)){
       // axi4lite_mem.io.req.waddr := addr
       io.axi_bus.req.bits.data  := io.exe_mem.bits.rs2_data << (addr(1,0) << 3)
       io.axi_bus.req.bits.mask  := Mux(req_typ === MT_B,1.U << addr(1,0),
                               Mux(req_typ === MT_H,3.U << addr(1,0),15.U))
    }   

    // read 
    // lsu should support mis-aligned access? or should based on slave type? 
    // val mis_aligned = Mux(mem_en && addr (1,0) =/= 0.U, true.B, false.B)
    val req_typ_reg = RegEnable(io.exe_mem.bits.ctrl_mem_typ,MT_X, to_axi_fire)
    val resp_data = io.axi_bus.resp.bits.data
    val aligned_resp_data = MuxCase(resp_data,Seq(
      (req_typ_reg === MT_B) -> Cat(Fill(24,resp_data(7)),resp_data(7,0)),
      (req_typ_reg === MT_H) -> Cat(Fill(16,resp_data(15)),resp_data(15,0)),
      (req_typ_reg === MT_BU) -> Cat(Fill(24,0.U),resp_data(7,0)),
      (req_typ_reg === MT_HU) -> Cat(Fill(16,0.U),resp_data(15,0))
    ))

 
    when (is_clint_req ){
        when (io.exe_mem.bits.ctrl_mem_fcn === M_XRD){
            io.clintIO.dr.en := true.B
            io.clintIO.dr.addr := addr
        } .otherwise{
            io.clintIO.dr.en := false.B
        }
    }

    val mem_port_resp_valid = io.axi_bus.resp.valid && state === s_bus_req 
    val mem_resp_valid  = Mux(in_clint, io.clintIO.dr.ready,    mem_port_resp_valid)
    val mem_exception   = Mux(in_clint, 0.U,                    (io.axi_bus.resp.bits.resp))
    val mem_data        = Mux(in_clint, io.clintIO.dr.data ,    (aligned_resp_data ))
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
    when(io.axi_bus.resp.valid && mem_en) {
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

