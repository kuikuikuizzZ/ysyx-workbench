package npc
import chisel3._
import chisel3.util._
import npc.common.{YSYX24100012Config, MemPortIo}   
import npc.Constants._

class regFileIo(implicit val conf: YSYX24100012Config) extends Bundle {
  val wb_data = Input(UInt(conf.xlen.W))
  val inst = Input(UInt(conf.xlen.W))
  val rf_wen = Input(Bool())
  val reg_to_dat_io = new regToDatIo()
}

class regToDatIo(implicit val conf: YSYX24100012Config) extends Bundle {
  val rs1_data = Output(UInt(conf.xlen.W))
  val rs2_data = Output(UInt(conf.xlen.W))
}

class RegFile(implicit val conf: YSYX24100012Config) extends Module {
  val io = IO(new regFileIo())
  io := DontCare
  val rs1_addr = io.inst(RS1_MSB, RS1_LSB)
  val rs2_addr = io.inst(RS2_MSB, RS2_LSB)
  val wb_addr  = io.inst(RD_MSB,  RD_LSB)
  
  // Register File
  val regfile = Mem(32, UInt(conf.xlen.W))

  when (io.rf_wen && (wb_addr =/= 0.U)) {
    regfile(wb_addr) := io.wb_data
  }

  io.reg_to_dat_io.rs1_data := Mux((rs1_addr =/= 0.U), regfile(rs1_addr), 0.asUInt(conf.xlen.W))
  io.reg_to_dat_io.rs2_data := Mux((rs2_addr =/= 0.U), regfile(rs2_addr), 0.asUInt(conf.xlen.W))
}