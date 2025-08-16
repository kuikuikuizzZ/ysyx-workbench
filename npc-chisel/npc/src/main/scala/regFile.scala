package npc
import chisel3._
import chisel3.util._
import npc.common.{ysyx_24100012_Config, MemPortIo}   
import npc.Constants._

class RegFileIo(implicit val conf: ysyx_24100012_Config) extends Bundle {
  val inst = Input(UInt(conf.xlen.W))
  val out = new RegFileOut()
  val wb = Flipped(new WBToRegIo())
}

class RegFileOut(implicit val conf: ysyx_24100012_Config) extends Bundle {
  val rs1_data = Output(UInt(conf.xlen.W))
  val rs2_data = Output(UInt(conf.xlen.W))
}

class ysyx_24100012_RegFile(implicit val conf: ysyx_24100012_Config) extends Module {
  val io = IO(new RegFileIo())
  io := DontCare
  val rs1_addr = io.inst(RS1_MSB, RS1_LSB)
  val rs2_addr = io.inst(RS2_MSB, RS2_LSB)
  val wb_addr  = io.inst(RD_MSB, RD_LSB)
  
  // Register File
  val regfile = Mem(16, UInt(conf.xlen.W))

  when (io.wb.rf_wen && (wb_addr =/= 0.U)) {
    regfile(wb_addr) := io.wb.data
  }

  io.out.rs1_data := Mux((rs1_addr =/= 0.U), regfile(rs1_addr), 0.asUInt(conf.xlen.W))
  io.out.rs2_data := Mux((rs2_addr =/= 0.U), regfile(rs2_addr), 0.asUInt(conf.xlen.W))
}