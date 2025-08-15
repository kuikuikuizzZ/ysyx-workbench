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
}

class ysyx_24100012_WBU(implicit val conf: ysyx_24100012_Config) extends Module {
    val io = IO(new Bundle {
        val ctl = Flipped(new CtlToWBIo())
        val exe = Flipped(new exeToWBUIo())
        val lsu = Flipped(new LsuToWBIo())
        val reg = new WBToRegIo()
        val debug = new WBDebugPort
    })

    io := DontCare


    io.reg.data := MuxCase( io.exe.alu_out, Seq(
                  (io.ctl.wb_sel === WB_ALU) -> io.exe.alu_out,
                  (io.ctl.wb_sel === WB_MEM) -> io.lsu.data,         //
                  (io.ctl.wb_sel === WB_PC4) -> io.exe.pc_plus4,
                  (io.ctl.wb_sel === WB_CSR) -> io.exe.csr_data
                ))

    io.reg.rf_wen :=  io.ctl.rf_wen

    ///////// DEBUG PORT
    val wbCount = RegInit(0.U(conf.perfCountBits.W))
    when(io.ctl.rf_wen === WB_MEM) {
        wbCount := wbCount + 1.U
    }
    io.debug.wbCount := wbCount
    ///////// END DEBUG
}