
package npc
{
import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._

class DatToCtlIo(implicit val conf: YSYX24100012Config) extends Bundle() 
{
   val inst   = Output(UInt(32.W))
   val br_eq  = Output(Bool())
   val br_lt  = Output(Bool())
   val br_ltu = Output(Bool())
   val ma_ls = Output(Bool())
}

class DpathIo(implicit val conf: YSYX24100012Config) extends Bundle() 
{
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val ctl  = Flipped(new CtlToDatIo())
   val dat  = new DatToCtlIo()
}


class YSYX24100012Dpath(implicit conf: YSYX24100012Config) extends Module
{
   val io = IO(new DpathIo())
   io := DontCare

   // Instruction Fetch
   val pc_next          = Wire(UInt(32.W))
   val pc_plus4         = Wire(UInt(32.W))
   val br_target        = Wire(UInt(32.W))
   val jmp_target       = Wire(UInt(32.W))
   val jump_reg_target  = Wire(UInt(32.W))
   // val exception_target = Wire(UInt(32.W))
   val xcpt             = Wire(Bool())
 
   // PC Register
   pc_next := MuxCase(pc_plus4, Seq(
                  (io.ctl.pc_sel === PC_BR)  -> br_target,
                  (io.ctl.pc_sel === PC_J )  -> jmp_target,
                  (io.ctl.pc_sel === PC_JR)  -> jump_reg_target
                  ))

   val pc_reg = RegInit(START_ADDR) 

   when (!io.ctl.stall) 
   {
      // pc_reg := Mux(xcpt, exception_target, pc_next)
      pc_reg :=  pc_next

   }

   pc_plus4 := (pc_reg + 4.asUInt(conf.xprlen.W))               

   
   io.imem.req.bits.addr := pc_reg
   io.imem.req.valid := true.B 
   // val inst = Mux(io.imem.resp.valid, io.imem.resp.bits.data, BUBBLE) 
   val inst = io.imem.resp.bits.data
   
   // Decode
   val rs1_addr = inst(RS1_MSB, RS1_LSB)
   val rs2_addr = inst(RS2_MSB, RS2_LSB)
   val wb_addr  = inst(RD_MSB,  RD_LSB)
   
   val wb_data = Wire(UInt(conf.xprlen.W))
 
   // Register File
   val regfile = Mem(32, UInt(conf.xprlen.W))

   when (io.ctl.rf_wen && !xcpt)
   {
      regfile(wb_addr) := wb_data
   }

   val rs1_data = Mux((rs1_addr =/= 0.U), regfile(rs1_addr), 0.asUInt(conf.xprlen.W))
   val rs2_data = Mux((rs2_addr =/= 0.U), regfile(rs2_addr), 0.asUInt(conf.xprlen.W))
   
   
   // immediates
   val imm_i = inst(31, 20) 
   val imm_s = Cat(inst(31, 25), inst(11,7))
   val imm_b = Cat(inst(31), inst(7), inst(30,25), inst(11,8))
   val imm_u = inst(31, 12)
   val imm_j = Cat(inst(31), inst(19,12), inst(20), inst(30,21))
   val imm_z = Cat(Fill(27,0.U), inst(19,15))

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(20,imm_i(11)), imm_i)
   val imm_s_sext = Cat(Fill(20,imm_s(11)), imm_s)
   val imm_b_sext = Cat(Fill(19,imm_b(11)), imm_b, 0.U)
   val imm_u_sext = Cat(imm_u, Fill(12,0.U))
   val imm_j_sext = Cat(Fill(11,imm_j(19)), imm_j, 0.U)

   val alu_op1 = MuxCase(0.U, Seq(
               (io.ctl.op1_sel === OP1_RS1) -> rs1_data,
               (io.ctl.op1_sel === OP1_IMU) -> imm_u_sext,
               (io.ctl.op1_sel === OP1_IMZ) -> imm_z
               )).asUInt

   val alu_op2 = MuxCase(0.U, Seq(
               (io.ctl.op2_sel === OP2_RS2) -> rs2_data,
               (io.ctl.op2_sel === OP2_PC)  -> pc_reg,
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
   br_target       := pc_reg + imm_b_sext
   jmp_target      := pc_reg + imm_j_sext
   jump_reg_target := Cat(alu_out(31,1), 0.U(1.W)) 

   // Control Status Registers
   // val csr = Module(new CSRFile())
   // csr.io := DontCare
   // csr.io.rw.addr := inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   // csr.io.rw.cmd   := io.ctl.csr_cmd 
   // csr.io.rw.wdata := alu_out

   // csr.io.retire    := !io.ctl.stall
   // csr.io.illegal   := io.ctl.illegal 
   // csr.io.pc        := pc_reg
   // exception_target := csr.io.evec

   // // Add your own uarch counters here!
   // csr.io.counters.foreach(_.inc := false.B)

   // WB Mux
   wb_data := MuxCase(alu_out, Seq(
                  (io.ctl.wb_sel === WB_ALU) -> alu_out,
                  (io.ctl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (io.ctl.wb_sel === WB_PC4) -> pc_plus4,
                  // (io.ctl.wb_sel === WB_CSR) -> csr.io.rw.rdata
                  ))
                                  
   // datapath to data memory outputs
   io.dmem.req.bits.addr  := alu_out
   io.dmem.req.bits.data := rs2_data.asUInt 

   // datapath to controlpath outputs
   io.dat.inst   := inst
   io.dat.br_eq  := (rs1_data === rs2_data)
   io.dat.br_lt  := (rs1_data.asSInt < rs2_data.asSInt) 
   io.dat.br_ltu := (rs1_data.asUInt < rs2_data.asUInt)
   
   val ma_typ           = Wire(Bool())
   val ma_load          = Wire(Bool())
   val ma_str           = Wire(Bool())
   val ma_instr         = Wire(Bool())
   io.dat.ma_ls  := ma_load || ma_str
   // memory access lw/sw should be xx00, lh/sh should be xxx0
   ma_typ        := MuxCase( false.B, Seq(
                     (io.ctl.mem_typ(1,0) === 2.U) -> alu_out(0), 
                     (io.ctl.mem_typ(1,0) === 3.U) -> alu_out(1,0).orR 
                     ))
   ma_load      := io.ctl.mem_en && !io.ctl.mem_fcn && ma_typ
   ma_str       := io.ctl.mem_en && io.ctl.mem_fcn && ma_typ
   ma_instr     := pc_next(1,0).orR

   // csr.io.xcpt    := ma_load || ma_str || ma_instr || io.ctl.illegal
   // csr.io.cause   := MuxCase(0.U, Array(
   //                   ma_instr -> Causes.misaligned_fetch.U,
   //                   io.ctl.illegal -> Causes.illegal_instruction.U,
   //                   ma_load -> Causes.misaligned_load.U,
   //                   ma_str  -> Causes.misaligned_store.U ))
   // csr.io.tval    := MuxCase(0.U, Array(
   //                   ma_instr -> pc_next,
   //                   io.ctl.illegal -> inst,
   //                   ma_load -> alu_out,
   //                   ma_str  -> alu_out ))
   // xcpt := ma_instr  || ma_load || ma_str || io.ctl.illegal || csr.io.eret   

   xcpt := ma_instr  || ma_load || ma_str || io.ctl.illegal   
 
}

 
}