
package npc.galois

import chisel3._
import chisel3.util._

import npc.galois.Constants._
import npc.common._
import npc._


class EXUDebugPort(implicit val conf: Config) extends Bundle() {

   val predict_wrong       = Output(UInt(conf.perfCountBits.W))
   val target_wrong        = Output(UInt(conf.perfCountBits.W))
   val br_wrong            = Output(UInt(conf.perfCountBits.W))
   val predict_hit         = Output(UInt(conf.perfCountBits.W))
   val predict_count       = Output(UInt(conf.perfCountBits.W))
}


class EXUIO(implicit val conf: Config) extends Bundle() 
{
   val rr_exe        = Flipped(new DecoupledIO(new BlockLineIO()))
   val rr_exe_mem    = Flipped(new DecoupledIO(new InstCtrlBlock()))
   val exe_out       = Output(new BlockLineIO())
   val exe_csr       = Output(new InstCtrlBlock())
   val exe_mem       = new DecoupledIO(new InstCtrlBlock())
   val debug         = Output(new EXUDebugPort())
   val redirect      = Input(Bool())
}

class EXU(implicit val conf: Config) extends Module with HasBPUParams 
{
   val io = IO(new EXUIO())
   val fire = RegNext(io.rr_exe.fire )

   val alu1 = Module(new ALU())
   val alu1_out = alu1.io.out
   alu1.io.inst := Mux(fire, io.rr_exe.bits.instA, 0.U.asTypeOf(new InstCtrlBlock()))

   val alu2 = Module(new ALU())
   val alu2_out = alu2.io.out
   alu2.io.inst := Mux(fire, io.rr_exe.bits.instB, 0.U.asTypeOf(new InstCtrlBlock()))

   val mem_fire = RegEnable(io.rr_exe_mem.fire,io.exe_mem.ready )
   val instC =  Mux(mem_fire,io.rr_exe_mem.bits, 0.U.asTypeOf(new InstCtrlBlock()))
   val alu3 = Module(new ALU())
   val alu3_out = alu3.io.out
   alu3.io.inst := instC
   val mem_out = InstCtrlBlock.copy(base=(instC),alu_out=Some(alu3_out))

   
   val is_csrC = instC.csr_ctrl.csr_cmd =/= CSR.N
   val is_memC = instC.mem_ctrl.mem_val
   val exceptionC = instC.exception =/= EXC_NORMAL
   val csr_files = Module(new CSRFiles())
   val csr_out =  InstCtrlBlock.copy(base=csr_files.io.out, finish=Some(instC.valid))
   csr_files.io.in := Mux(is_csrC || exceptionC, instC, 0.U.asTypeOf(new InstCtrlBlock()))


   when (io.redirect){
      io.exe_out.instA  := 0.U.asTypeOf(new InstCtrlBlock())
      io.exe_out.instB  := 0.U.asTypeOf(new InstCtrlBlock())
      io.exe_mem.valid  := false.B
      io.exe_mem.bits   := 0.U.asTypeOf(new InstCtrlBlock())
      io.exe_csr        := 0.U.asTypeOf(new InstCtrlBlock())
   } .otherwise {
      // register read has confirm is int op
      val instA = InstCtrlBlock.copy(base=(io.rr_exe.bits.instA),alu_out=Some(alu1_out),wb_data = Some(alu1_out), finish=Some(true.B))
      val instB = InstCtrlBlock.copy(base=(io.rr_exe.bits.instB),alu_out=Some(alu2_out),wb_data = Some(alu2_out), finish=Some(true.B))
      io.exe_out.instA  := Mux(fire, instA, 0.U.asTypeOf(new InstCtrlBlock()))
      io.exe_out.instB  := Mux(fire, instB, 0.U.asTypeOf(new InstCtrlBlock()))
      io.exe_mem.valid  := mem_fire
      io.exe_mem.bits   := Mux(is_csrC, 0.U.asTypeOf(new InstCtrlBlock),mem_out)
      io.exe_csr        := Mux(is_csrC,csr_out ,0.U.asTypeOf(new InstCtrlBlock))
   }
   io.rr_exe_mem.ready := io.exe_mem.ready
   io.rr_exe.ready := true.B

   io.debug  := DontCare
}


class ALU (implicit val conf: Config) extends OOOModule{ 
   val io = IO(new Bundle { 
      val inst  = Input(new InstCtrlBlock)
      val out   = UInt(conf.xlen.W)
   })
   val alu_op1 = Mux(io.inst.alu_ctrl.op1_sel =/= 0.U, io.inst.pc,
                        io.inst.rs1_data.asUInt)
   val alu_op2 = Mux(io.inst.alu_ctrl.op2_sel =/= 0.U, io.inst.imm,
                        io.inst.rs2_data.asUInt)
   // ALU
   val alu_out   = Wire(UInt(conf.xprlen.W))
   val alu_shamt = alu_op2(4,0).asUInt

   alu_out := MuxCase(0.U, Seq(
                  (io.inst.alu_ctrl.alu_fun === ALU_ADD)  -> (alu_op1 + alu_op2).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_SUB)  -> (alu_op1 - alu_op2).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_AND)  -> (alu_op1 & alu_op2).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_OR)   -> (alu_op1 | alu_op2).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_XOR)  -> (alu_op1 ^ alu_op2).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_SLT)  -> (alu_op1.asSInt < alu_op2.asSInt).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_SLTU) -> (alu_op1 < alu_op2).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_SLL)  -> ((alu_op1 << alu_shamt)(conf.xprlen-1, 0)).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_SRA)  -> (alu_op1.asSInt >> alu_shamt).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_SRL)  -> (alu_op1 >> alu_shamt).asUInt,
                  (io.inst.alu_ctrl.alu_fun === ALU_COPY_1)-> alu_op1,
                  (io.inst.alu_ctrl.alu_fun === ALU_COPY_2)-> alu_op2
                  ))
   io.out := alu_out
}


class CSRFiles(implicit val conf: Config) extends Module {
    val io = IO(new Bundle{
         val in   = Input(new InstCtrlBlock)
         val out  = Output(new InstCtrlBlock)
    }) 
    // Control Status Registers
    val csr = Module(new CSRFile())
    csr.io.decode.csr       := io.in.inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
    csr.io.rw.cmd           := io.in.csr_ctrl.csr_cmd
    csr.io.rw.wdata         := io.in.rs1_data   // alu_sel is copy_rs1
    csr.io.exception        := io.in.exception
    csr.io.pc               := io.in.pc
    val wb_data             = Mux(io.in.wb_ctrl.wb_sel === WB_CSR, csr.io.rw.rdata, io.in.wb_data)
    val pc_sel              = Mux(csr.io.eret, PC_EXC, io.in.pc_sel)
    val out                 = InstCtrlBlock.csrOp(base = io.in,
                                  wb_data = wb_data,
                                  target = csr.io.evec,
                                  pc_sel = pc_sel,
                                  eret = csr.io.eret,
                                  ebreak = csr.io.insn_break)
    io.out := out
}


class BJU (implicit val conf: Config) extends OOOModule with HasBPUParams { 
   val io = IO(new Bundle { 
      val inst  = Input(new InstCtrlBlock)
      val out   = Output(new InstCtrlBlock)
      val debug = new EXUDebugPort()
   })
   val alu_op1 = Mux(io.inst.alu_ctrl.op1_sel =/= 0.U, io.inst.pc,
                        io.inst.rs1_data.asUInt)
   val alu_op2 = Mux(io.inst.alu_ctrl.op2_sel =/= 0.U, io.inst.imm,
                        io.inst.rs2_data.asUInt)

   // Branch/Jump Target Calculation
   val pc_plus4    = ( io.inst.pc + 4.U)(conf.xprlen-1,0)
   val brjmp_offset                 = io.inst.imm
   val exe_brjmp_target             = io.inst.pc + brjmp_offset
   val exe_jump_reg_target          = (alu_op1 + alu_op2)(conf.xprlen-1,0)

   ////// Branch Logic
   val br_eq    = (alu_op1     ===  io.inst.rs2_data)
   val br_lt    = (alu_op1.asSInt < io.inst.rs2_data.asSInt) 
   val br_ltu   = (alu_op1.asUInt < io.inst.rs2_data.asUInt)
  
   val exe_br_type = io.inst.br_ctrl.br_type
   val taken = MuxLookup(exe_br_type, false.B)( Seq(
      BR_NE  -> !br_eq,
      BR_EQ  -> br_eq,
      BR_GE  -> !br_lt,
      BR_GEU -> !br_ltu,
      BR_LT  -> br_lt,
      BR_LTU -> br_ltu
   ))

   val base_sel = MuxLookup(exe_br_type, PC_4)(Seq(
      BR_J  -> PC_BRJMP,
      BR_JR -> PC_JALR
   ))

   val is_cond_br = exe_br_type === BR_NE || exe_br_type === BR_EQ ||
                  exe_br_type === BR_GE || exe_br_type === BR_GEU ||
                  exe_br_type === BR_LT || exe_br_type === BR_LTU
               
   val is_jump   = exe_br_type === BR_J || exe_br_type === BR_JR

   val cond_sel = Mux(taken, PC_BRJMP, PC_4)

   val bpu_resp            =  io.inst.br_ctrl.bpu_resp
   val br_pc_sel           = Mux(is_cond_br, PC_BRJMP, base_sel)
   val groupIdx            = getGroupOffset(io.inst.pc)
   val target              = Mux(taken && is_cond_br || exe_br_type === BR_J,  exe_brjmp_target,
                              Mux(exe_br_type === BR_JR,   exe_jump_reg_target,
                              pc_plus4))
   val predict_wrong       = Mux(!taken && is_cond_br, bpu_resp.brIdx(groupIdx).asBool, 
                                  (!bpu_resp.brIdx(groupIdx).asBool || target =/= bpu_resp.target))
   val should_redirect     =  predict_wrong && (is_jump || is_cond_br) 

   val ctrl_exe_pc_sel     =  Mux(exe_br_type === BR_N || !should_redirect, cond_sel,br_pc_sel)
  
   io.out := InstCtrlBlock.bjuOp(
      pc                     = io.inst.pc,
      target                 = target,
      pc_sel                 = ctrl_exe_pc_sel,
      should_redirect        = should_redirect,
      taken                  = taken,
      wb_data                = pc_plus4,
      predict_wrong          = predict_wrong,
      redirect_type          = io.inst.br_ctrl.redirect_type,
      base                   = io.inst
   ) 
   

    /////////// DEBUG
   val perfCounters = RegInit(VecInit(Seq.fill(5)(0.U(conf.perfCountBits.W))))
   val Seq( predict_wrong_count, target_wrong_count, br_wrong_count ,predict_hit_count, predict_count ) = perfCounters

   val target_wrong = !bpu_resp.brIdx(0) || target =/= bpu_resp.target
   val br_wrong = predict_wrong && (is_cond_br)
   when(bpu_resp.valid && io.inst.valid){
      val predict_taken = bpu_resp.brIdx.asUInt.orR
      when(predict_wrong){ predict_wrong_count := predict_wrong_count + 1.U }
      when(target_wrong){ target_wrong_count := target_wrong_count + 1.U }
      when(br_wrong){ br_wrong_count := br_wrong_count + 1.U }
      when (target === bpu_resp.target || (!taken && !predict_taken )) { predict_hit_count := predict_hit_count + 1.U}
      predict_count := predict_count + 1.U
   }
   io.debug.predict_wrong := predict_wrong_count
   io.debug.target_wrong := target_wrong_count
   io.debug.br_wrong := br_wrong_count
   io.debug.predict_hit := predict_hit_count
   io.debug.predict_count := predict_count
}