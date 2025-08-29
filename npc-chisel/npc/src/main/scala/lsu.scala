
package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._
import javax.xml.transform.OutputKeys

class LSUPipeIO(implicit val conf: ysyx_24100012_Config) extends Bundle() {
    val wbaddr     = Output(UInt(conf.xprlen.W))
    val data       = Output(UInt(conf.xprlen.W))
    val ctrl_rf_wen     = Output(Bool())
    val ebreak     = Output(Bool())
}

class CtlToLSUlIO (implicit val conf: ysyx_24100012_Config) extends Bundle() {
    val mem_exception = Output(Bool())
}

class LSUTOCtlIO (implicit val conf: ysyx_24100012_Config) extends Bundle() {
    val resp_valid   = Output(Bool())
    val ctrl_mem_val = Output(Bool())
}


class LSUDebugPort(implicit val conf: ysyx_24100012_Config) extends Bundle {
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

class ysyx_24100012_CSRFiles(implicit val conf: ysyx_24100012_Config) extends Module {
    val io = IO(new Bundle{
        val inst                = Input(UInt(conf.xlen.W))
        val csr_cmd             = Input(UInt(CSR.N.getWidth.W))
        val pc                  = Input(UInt(conf.xprlen.W))
        val alu_out             = Input(UInt(conf.xlen.W))
        val exception_target    = Output(UInt(conf.xprlen.W))
        val rdata               = Output(UInt(conf.xlen.W))
        val ebreak              = Output(Bool())
    }) 
    // Control Status Registers
    val csr = Module(new ysyx_24100012_CSRFile())
    csr.io := DontCare
    csr.io.decode.csr   := io.inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
    csr.io.rw.cmd       := io.csr_cmd
    csr.io.rw.wdata     := io.alu_out
    // csr.io.retire    := !(io.exe_mem.bits.stall || io.exe_mem.bits.exception)
    // csr.io.exception := io.exe_mem.bits.exception
    csr.io.pc           := io.pc
    io.exception_target := csr.io.evec
    io.rdata            := csr.io.rw.rdata    

    // io.dat.csr_eret := csr.io.eret
    io.ebreak := csr.io.insn_break
    // Add your own uarch counters here!
    // csr.io.counters.foreach(_.inc := false.B)
}
class LSUIO(implicit val conf: ysyx_24100012_Config) extends Bundle {
    val exe_mem             = Flipped(new DecoupledIO(new EXEPipeIO()))
    val mem_wb              = new DecoupledIO(new LSUPipeIO)
    val port                = new MemPortIo(conf.xprlen)
    val debug               = new LSUDebugPort
    val exception_target    = Output(UInt(conf.xprlen.W))
    val ctl                 = Flipped(new CtlToLSUlIO)
    val to_ctl             = new LSUTOCtlIO
    
    val clintIO = Flipped(  new Bundle{
            val dr      =   new AXIRport(conf.xprlen, conf.xlen)
            val dw      =   new AXIWport(conf.xprlen, conf.xlen)
        })
}
class ysyx_24100012_LSU(implicit val conf: ysyx_24100012_Config) extends Module {
    val io = IO(new LSUIO())
    io := DontCare
    io.exe_mem.ready := true.B
    
    // val valid = Wire(Bool())
    val mem_data = Wire(UInt(conf.xlen.W))
    val addr = io.exe_mem.bits.alu_out
    val mem_en = io.exe_mem.bits.ctrl_mem_val
    val in_clint = addr >= CLINT_BASE && addr < (CLINT_BASE + CLINT_SIZE)
    val csr_files = Module(new ysyx_24100012_CSRFiles)
    csr_files.io.pc         := io.exe_mem.bits.pc   
    csr_files.io.inst       := io.exe_mem.bits.inst
    csr_files.io.csr_cmd    := io.exe_mem.bits.ctrl_csr_cmd
    csr_files.io.alu_out    := io.exe_mem.bits.alu_out
    io.exception_target     := csr_files.io.exception_target    
    io.mem_wb.bits.ebreak   := csr_files.io.ebreak

    when (mem_en && in_clint ){
        io.port.req.valid    := false.B
        when (io.exe_mem.bits.ctrl_mem_fcn === M_XRD){
            io.clintIO.dr.en := true.B
            io.clintIO.dr.addr := addr
            mem_data := io.clintIO.dr.data
        } .otherwise{
            io.clintIO.dr.en := false.B
        }
    } .otherwise {
        io.port.req.valid    := mem_en
        io.port.req.bits.fcn := io.exe_mem.bits.ctrl_mem_fcn
        io.port.req.bits.typ := io.exe_mem.bits.ctrl_mem_typ
        io.port.req.bits.addr := addr
        io.port.req.bits.data := io.exe_mem.bits.rs2_data 
        mem_data :=  io.port.resp.bits.data
    }
    
    // io.port.req.valid    := mem_en
    // io.port.req.bits.fcn := io.exe_mem.bits.ctrl_mem_fcn
    // io.port.req.bits.typ := io.exe_mem.bits.ctrl_mem_typ
    // io.port.req.bits.addr := addr
    // io.port.req.bits.data := io.exe_mem.bits.rs2_data 
    // io.to_ctl.resp_valid :=   io.port.resp.valid
    // mem_data :=  io.port.resp.bits.data

    io.to_ctl.resp_valid    := Mux(in_clint, io.clintIO.dr.ready, io.port.resp.valid)
    io.to_ctl.ctrl_mem_val  := mem_en
    // WB Mux
    val wbdata = MuxCase(io.exe_mem.bits.alu_out, Array(
                  (io.exe_mem.bits.ctrl_wb_sel === WB_ALU) -> io.exe_mem.bits.alu_out,
                  (io.exe_mem.bits.ctrl_wb_sel === WB_PC4) -> io.exe_mem.bits.alu_out,
                  (io.exe_mem.bits.ctrl_wb_sel === WB_MEM) -> mem_data,
                  (io.exe_mem.bits.ctrl_wb_sel === WB_CSR) -> csr_files.io.rdata
                  ))

    // io.mem_wb.valid         := io.exe_mem.valid  && !io.ctl.mem_exception
    io.mem_wb.valid         := io.exe_mem.valid && io.port.resp.valid
    io.mem_wb.bits.data     := wbdata
    io.mem_wb.bits.wbaddr   := io.exe_mem.bits.wbaddr
    io.mem_wb.bits.ctrl_rf_wen   := io.exe_mem.bits.ctrl_rf_wen

    /* Debug */
    val storeCnt        = RegInit(0.U(conf.perfCountBits.W))
    val loadCnt         = RegInit(0.U(conf.perfCountBits.W))
    io.debug.mem_en     := mem_en
    io.debug.addr       := addr
    io.debug.fcn        := io.exe_mem.bits.ctrl_mem_fcn
    io.debug.wdata      := io.exe_mem.bits.rs2_data 
    io.debug.rdata      := io.port.resp.bits.data
    io.debug.valid      := io.port.resp.valid
    io.debug.typ        := io.exe_mem.bits.ctrl_mem_typ
    when(io.port.req.valid) {
      when(io.exe_mem.bits.ctrl_mem_fcn === M_XWR) {
        storeCnt := storeCnt + 1.U
      }.otherwise {
        loadCnt := loadCnt + 1.U
      }
    }
    io.debug.loadCount  := loadCnt
    io.debug.storeCount := storeCnt
}

