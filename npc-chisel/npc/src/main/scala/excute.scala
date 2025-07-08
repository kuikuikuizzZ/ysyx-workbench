
package npc
{
import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._


class DpathIo(implicit val conf: YSYX24100012Config) extends Bundle() 
{
   val inst = Input(UInt(conf.xprlen.W))
   val ctl  = Flipped(new CtlToDatIo())
   val ebreak = Output(Bool())
   val targets = new PCTargets()
   val pc_io = Flipped(new PCOut())
   val reg_in = Flipped(new RegFileOut())
   val exe_lsu = new exeToLSUIo()
   val exe_wbu = new exeToWBUIo()
}

class exeToLSUIo(implicit val conf: YSYX24100012Config) extends Bundle() {
  val addr = Output(UInt(conf.xprlen.W))
  val data = Output(UInt(conf.xprlen.W))
}

class exeToWBUIo(implicit val conf: YSYX24100012Config) extends Bundle {
   val alu_out = Output(UInt(conf.xprlen.W))
   val csr_data = Output(UInt(conf.xprlen.W))
   val pc_plus4 = Output(UInt(conf.xprlen.W))
}

class PCTargets(implicit val conf: YSYX24100012Config) extends Bundle() {
   val pc_sel           =  Output(UInt(PC_4.getWidth.W))
   val br_target        =  Output(UInt(conf.xprlen.W))
   val jmp_target       =  Output(UInt(conf.xprlen.W))
   val jump_reg_target  =  Output(UInt(conf.xprlen.W))
   val exception_target =  Output(UInt(conf.xprlen.W))
}

class YSYX24100012Dpath(implicit conf: YSYX24100012Config) extends Module
{
   val io = IO(new DpathIo())
   io := DontCare

   // immediates
   val imm_i = io.inst(31, 20) 
   val imm_s = Cat(io.inst(31, 25), io.inst(11,7))
   val imm_b = Cat(io.inst(31), io.inst(7), io.inst(30,25), io.inst(11,8))
   val imm_u = io.inst(31, 12)
   val imm_j = Cat(io.inst(31), io.inst(19,12), io.inst(20), io.inst(30,21))
   val imm_z = Cat(Fill(27,0.U), io.inst(19,15))

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(20,imm_i(11)), imm_i)
   val imm_s_sext = Cat(Fill(20,imm_s(11)), imm_s)
   val imm_b_sext = Cat(Fill(19,imm_b(11)), imm_b, 0.U)
   val imm_u_sext = Cat(imm_u, Fill(12,0.U))
   val imm_j_sext = Cat(Fill(11,imm_j(19)), imm_j, 0.U)

   val alu_op1 = MuxCase(0.U, Seq(
               (io.ctl.op1_sel === OP1_RS1) -> io.reg_in.rs1_data,
               (io.ctl.op1_sel === OP1_IMU) -> imm_u_sext,
               (io.ctl.op1_sel === OP1_IMZ) -> imm_z
               )).asUInt

   val alu_op2 = MuxCase(0.U, Seq(
               (io.ctl.op2_sel === OP2_RS2) -> io.reg_in.rs2_data,
               (io.ctl.op2_sel === OP2_PC)  -> io.pc_io.pc,
               (io.ctl.op2_sel === OP2_IMI) -> imm_i_sext,
               (io.ctl.op2_sel === OP2_IMS) -> imm_s_sext
               )).asUInt


   // ALU
   val alu_out   = Wire(UInt(conf.xprlen.W))

   val alu_shamt = alu_op2(4,0).asUInt

   alu_out := MuxCase(0.U, Seq(
                  (io.ctl.alu_fun === ALU_ADD)  -> (alu_op1 + alu_op2).asUInt,
                  (io.ctl.alu_fun === ALU_SUB)  -> (alu_op1 - alu_op2).asUInt,
                  (io.ctl.alu_fun === ALU_AND)  -> (alu_op1 & alu_op2).asUInt,
                  (io.ctl.alu_fun === ALU_OR)   -> (alu_op1 | alu_op2).asUInt,
                  (io.ctl.alu_fun === ALU_XOR)  -> (alu_op1 ^ alu_op2).asUInt,
                  (io.ctl.alu_fun === ALU_SLT)  -> (alu_op1.asSInt < alu_op2.asSInt).asUInt,
                  (io.ctl.alu_fun === ALU_SLTU) -> (alu_op1 < alu_op2).asUInt,
                  (io.ctl.alu_fun === ALU_SLL)  -> ((alu_op1 << alu_shamt)(conf.xprlen-1, 0)).asUInt,
                  (io.ctl.alu_fun === ALU_SRA)  -> (alu_op1.asSInt >> alu_shamt).asUInt,
                  (io.ctl.alu_fun === ALU_SRL)  -> (alu_op1 >> alu_shamt).asUInt,
                  (io.ctl.alu_fun === ALU_COPY1)-> alu_op1
                  ))

   // Branch/Jump Target Calculation
   io.targets.br_target       := io.pc_io.pc + imm_b_sext
   io.targets.jmp_target      := io.pc_io.pc + imm_j_sext
   io.targets.jump_reg_target := Cat(alu_out(31,1), 0.U(1.W)) 

   // Control Status Registers
   val csr = Module(new CSRFile())
   csr.io := DontCare
   csr.io.decode.csr := io.inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   csr.io.rw.cmd   := io.ctl.csr_cmd
   csr.io.rw.wdata := alu_out

   // csr.io.retire    := !(io.ctl.stall || io.ctl.exception)
   csr.io.exception := io.ctl.exception
   csr.io.pc        := io.pc_io.pc
   io.targets.exception_target := csr.io.evec

   // io.dat.csr_eret := csr.io.eret
   io.ebreak := csr.io.insn_break
   // Add your own uarch counters here!
   // csr.io.counters.foreach(_.inc := false.B)
                                  
   // datapath to controlpath outputs
   val br_eq  = (io.reg_in.rs1_data === io.reg_in.rs2_data)
   val br_lt  = (io.reg_in.rs1_data.asSInt < io.reg_in.rs2_data.asSInt) 
   val br_ltu = (io.reg_in.rs1_data.asUInt < io.reg_in.rs2_data.asUInt)
   

   // Branch Logic   
   io.targets.pc_sel := Mux( csr.io.eret  ||
                         io.ctl.exception      ,  PC_EXC,
                     Mux(io.ctl.br_type === BR_N  ,  PC_4,
                     Mux(io.ctl.br_type === BR_NE ,  Mux(!br_eq,  PC_BR, PC_4),
                     Mux(io.ctl.br_type === BR_EQ ,  Mux( br_eq,  PC_BR, PC_4),
                     Mux(io.ctl.br_type === BR_GE ,  Mux(!br_lt,  PC_BR, PC_4),
                     Mux(io.ctl.br_type === BR_GEU,  Mux(!br_ltu, PC_BR, PC_4),
                     Mux(io.ctl.br_type === BR_LT ,  Mux( br_lt,  PC_BR, PC_4),
                     Mux(io.ctl.br_type === BR_LTU,  Mux( br_ltu, PC_BR, PC_4),
                     Mux(io.ctl.br_type === BR_J  ,  PC_J,
                     Mux(io.ctl.br_type === BR_JR ,  PC_JR,
                                                 PC_4))))))))))

   // datapath to data memory outputs
   io.exe_lsu.addr  := alu_out
   io.exe_lsu.data := io.reg_in.rs2_data.asUInt 
   io.exe_wbu.alu_out := alu_out
   io.exe_wbu.csr_data := csr.io.rw.rdata
   io.exe_wbu.pc_plus4 := io.pc_io.pc_plus4
}

 
}