package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._

class WBUDebugPort(implicit val conf: ysyx_24100012_Config) extends Bundle {
    val wbCount = Output(UInt(conf.perfCountBits.W))
}

class WBToRegIo(implicit val conf: ysyx_24100012_Config) extends Bundle {
    val rf_wen = Output(Bool())
    val data = Output(UInt(conf.xprlen.W))
    val wbaddr = Output(UInt(5.W))
}


class ysyx_24100012_WBU(implicit val conf: ysyx_24100012_Config) extends Module {
    val io = IO(new Bundle {
        val mem_wb = Flipped(new DecoupledIO (new LSUPipeIO()))
        val reg = new DecoupledIO(new WBToRegIo())
        val debug = new WBUDebugPort()
        val ctl = new CtrlSignalIO()
        val ebreak = Output(Bool())
    })

    io := DontCare
    io.ebreak            := io.mem_wb.bits.ebreak
    io.mem_wb.ready := true.B

    // full stall to valid
    // when (io.ctl.full_stall) 
    when (io.mem_wb.valid)
    {
        io.reg.valid         := io.mem_wb.valid && !io.ctl.mem_exception 
        io.reg.bits.data     := io.mem_wb.bits.data
        io.reg.bits.wbaddr   := io.mem_wb.bits.wbaddr
        io.reg.bits.rf_wen   := Mux(io.ctl.mem_exception, false.B,io.mem_wb.bits.ctrl_rf_wen)
    }
    .otherwise
    {
        io.reg.valid         := false.B
        io.reg.bits.rf_wen   := false.B
    }
    
    ///////// DEBUG PORT
    val wbCount = RegInit(0.U(conf.perfCountBits.W))
    when(io.mem_wb.bits.ctrl_rf_wen === WB_MEM) {
        wbCount := wbCount + 1.U
    }
    io.debug.wbCount := wbCount
    ///////// END DEBUG
}