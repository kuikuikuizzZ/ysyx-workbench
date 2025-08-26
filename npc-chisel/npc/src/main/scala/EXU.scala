
package npc
{
import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._


class EXEPipeIO(implicit val conf: ysyx_24100012_Config) extends Bundle() {
   // Memory State
   val inst          = Output(UInt(conf.xlen.W))
   val pc            = Output(UInt(conf.xprlen.W))
   val wbaddr        = Output(UInt(5.W))
   val rs1_addr      = Output(UInt(5.W))
   val rs2_addr      = Output(UInt(5.W))
   val op1_data      = Output(UInt(conf.xprlen.W))
   val op2_data      = Output(UInt(conf.xprlen.W))
   val rs2_data      = Output(UInt(conf.xprlen.W))
   val alu_out       = Output(UInt(conf.xlen.W))
   val ctrl_wb_sel        = Output(UInt())
   val ctrl_rf_wen        = Output(Bool())
   val ctrl_mem_val       = Output(Bool())
   val ctrl_mem_fcn       = Output(UInt(M_X.getWidth.W)) 
   val ctrl_mem_typ       = Output(UInt(MT_X.getWidth.W))
   val ctrl_csr_cmd       = Output(UInt(CSR.N.getWidth.W))
}

class EXUToIFUOut (implicit val conf: ysyx_24100012_Config) extends Bundle() {
   val exe_brjmp_target    =   Output(UInt(conf.xprlen.W))
   val exe_jump_reg_target =   Output(UInt(conf.xprlen.W))
}

class DpathIo(implicit val conf: ysyx_24100012_Config) extends Bundle() 
{
   val dec_exe = Flipped(new DecoupledIO(new DecPipeIO()))
   val exe_mem = new DecoupledIO(new EXEPipeIO())
   val ifu_out = new EXUToIFUOut()
}

class ysyx_24100012_EXU(implicit conf: ysyx_24100012_Config) extends Module
{
   val io = IO(new DpathIo())
   io := DontCare
   val alu_op1 = io.dec_exe.bits.op1_data.asUInt()
   val alu_op2 = io.dec_exe.bits.op2_data.asUInt()

   // ALU
   val alu_out   = Wire(UInt(conf.xprlen.W))
   val alu_shamt = alu_op2(4,0).asUInt
   val adder_out = (alu_op1 + alu_op2)(conf.xprlen-1,0)

   alu_out := MuxCase(0.U, Seq(
                  (io.dec_exe.alu_fun === ALU_ADD)  -> (alu_op1 + alu_op2).asUInt,
                  (io.dec_exe.alu_fun === ALU_SUB)  -> (alu_op1 - alu_op2).asUInt,
                  (io.dec_exe.alu_fun === ALU_AND)  -> (alu_op1 & alu_op2).asUInt,
                  (io.dec_exe.alu_fun === ALU_OR)   -> (alu_op1 | alu_op2).asUInt,
                  (io.dec_exe.alu_fun === ALU_XOR)  -> (alu_op1 ^ alu_op2).asUInt,
                  (io.dec_exe.alu_fun === ALU_SLT)  -> (alu_op1.asSInt < alu_op2.asSInt).asUInt,
                  (io.dec_exe.alu_fun === ALU_SLTU) -> (alu_op1 < alu_op2).asUInt,
                  (io.dec_exe.alu_fun === ALU_SLL)  -> ((alu_op1 << alu_shamt)(conf.xprlen-1, 0)).asUInt,
                  (io.dec_exe.alu_fun === ALU_SRA)  -> (alu_op1.asSInt >> alu_shamt).asUInt,
                  (io.dec_exe.alu_fun === ALU_SRL)  -> (alu_op1 >> alu_shamt).asUInt,
                  (io.dec_exe.alu_fun === ALU_COPY1)-> alu_op1
                  ))

   // Branch/Jump Target Calculation
   val pc_plus4    = ( io.dec_exe.bits.pc + 4.U)(conf.xprlen-1,0)
   val brjmp_offset                 = io.dec_exe.bits.op2_data
   io.ifu_out.exe_brjmp_target      := io.dec_exe.bits.pc + brjmp_offset
   io.ifu_out.exe_jump_reg_target   := adder_out
   
   

   io.exe_mem.valid              := io.dec_exe.valid
   io.exe_mem.bits.alu_out       := Mux((io.dec_exe.bits.ctrl_wb_sel === WB_PC4), pc_plus4, alu_out)
   io.exe_mem.bits.pc            := io.dec_exe.bits.pc
   io.exe_mem.bits.inst          := io.dec_exe.bits.inst
   io.exe_mem.bits.wbaddr        := io.dec_exe.bits.wbaddr
   io.exe_mem.bits.rs1_addr      := io.dec_exe.bits.rs1_addr
   io.exe_mem.bits.rs2_addr      := io.dec_exe.bits.rs2_addr
   io.exe_mem.bits.op1_data      := io.dec_exe.bits.op1_data
   io.exe_mem.bits.op2_data      := io.dec_exe.bits.op2_data
   io.exe_mem.bits.rs2_data      := io.dec_exe.bits.rs2_data
   io.exe_mem.bits.ctrl_rf_wen   := io.dec_exe.bits.ctrl_rf_wen
   io.exe_mem.bits.ctrl_mem_val  := io.dec_exe.bits.ctrl_mem_val
   io.exe_mem.bits.ctrl_mem_fcn  := io.dec_exe.bits.ctrl_mem_fcn
   io.exe_mem.bits.ctrl_mem_typ  := io.dec_exe.bits.ctrl_mem_typ
   io.exe_mem.bits.ctrl_wb_sel   := io.dec_exe.bits.ctrl_wb_sel
   io.exe_mem.bits.ctrl_csr_cmd  := io.dec_exe.bits.ctrl_csr_cmd



   // // Control Status Registers
   // val csr = Module(new ysyx_24100012_CSRFile())
   // csr.io := DontCare
   // csr.io.decode.csr := io.inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   // csr.io.rw.cmd   := Mux(io.ifu_valid, io.ctl.csr_cmd,CSR.N)
   // csr.io.rw.wdata := alu_out

   // // csr.io.retire    := !(io.ctl.stall || io.ctl.exception)
   // csr.io.exception := io.ctl.exception
   // csr.io.pc        := io.pc_io.pc
   // io.targets.exception_target := csr.io.evec

   // // io.dat.csr_eret := csr.io.eret
   // io.ebreak := csr.io.insn_break
   // // Add your own uarch counters here!
   // // csr.io.counters.foreach(_.inc := false.B)

}

 
}