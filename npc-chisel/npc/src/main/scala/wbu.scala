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

class WBToCtlIO (implicit val conf: ysyx_24100012_Config) extends Bundle() {
    val wb_reg_wbaddr = Output(UInt(5.W))
    val wb_reg_ctrl_rf_wen = Output(Bool())
}


class ysyx_24100012_WBU(implicit val conf: ysyx_24100012_Config) extends Module {
    val io = IO(new Bundle {
        val mem_wb = Flipped(new DecoupledIO (new LSUPipeIO()))
        val reg = new DecoupledIO(new WBToRegIo())
        val debug = new WBUDebugPort()
        val to_ctl = new WBToCtlIO()
        val ebreak = Output(Bool())
    })

    io := DontCare
    io.reg.bits.data     := io.mem_wb.bits.data
    io.reg.bits.wbaddr   := io.mem_wb.bits.wbaddr
    io.reg.bits.rf_wen   := io.mem_wb.bits.ctrl_rf_wen
    io.reg.valid         := io.mem_wb.valid
    io.ebreak            := io.mem_wb.bits.ebreak
    ///////// DEBUG PORT
    val wbCount = RegInit(0.U(conf.perfCountBits.W))
    when(io.mem_wb.bits.ctrl_rf_wen === WB_MEM) {
        wbCount := wbCount + 1.U
    }
    io.debug.wbCount := wbCount
    ///////// END DEBUG
}