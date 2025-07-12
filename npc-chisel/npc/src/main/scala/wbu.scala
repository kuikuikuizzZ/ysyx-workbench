package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._

class WBToRegIo(implicit val conf: YSYX24100012Config) extends Bundle {
    val rf_wen = Output(Bool())
    val data = Output(UInt(conf.xprlen.W))
}

class YSYX2400012WBU(implicit val conf: YSYX24100012Config) extends Module {
    val io = IO(new Bundle {
        val stall = Input(Bool())
        val ctl = Flipped(new CtlToWBIo())
        val exe = Flipped(new exeToWBUIo())
        val lsu = Flipped(new LsuToWBIo())
        val reg = new WBToRegIo()
    })

    io := DontCare

    // val reg_alu_out  = RegNext(io.exe.alu_out)
    // val reg_csr_data = RegNext(io.exe.csr_data)
    // val reg_pc_plus4 = RegNext(io.exe.pc_plus4)
    // val reg_rf_wen   = RegNext(io.ctl.rf_wen)
    io.reg.data := MuxCase( io.exe.alu_out, Seq(
                  (io.ctl.wb_sel === WB_ALU) -> io.exe.alu_out,
                  (io.ctl.wb_sel === WB_MEM) -> io.lsu.data,         //
                  (io.ctl.wb_sel === WB_PC4) -> io.exe.pc_plus4,
                  (io.ctl.wb_sel === WB_CSR) -> io.exe.csr_data
                ))
    // io.reg.data := MuxCase( io.exe.alu_out, Seq(
    //               (io.ctl.wb_sel === WB_ALU) -> reg_alu_out,
    //               (io.ctl.wb_sel === WB_MEM) -> io.lsu.data, 
    //               (io.ctl.wb_sel === WB_PC4) -> reg_pc_plus4,
    //               (io.ctl.wb_sel === WB_CSR) -> reg_pc_plus4
    //               ))
    // io.reg.rf_wen   := Mux(io.stall || io.ctl.exception, false.B, io.ctl.rf_wen)
    io.reg.rf_wen :=  io.ctl.rf_wen
}