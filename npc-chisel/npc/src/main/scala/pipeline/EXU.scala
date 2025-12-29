
package npc.pipeline
{
import chisel3._
import chisel3.util._

import npc.pipeline.Constants._
import npc._
import npc.common._
import npc._



class EXEPipeIO(implicit val conf: Config) extends Bundle() {
   // Memory State
   val csr_inst         = Output(UInt(12.W))
   val pc               = Output(UInt(conf.xprlen.W))
   val pc_valid         = Output(Bool())
   val wbaddr           = Output(UInt(5.W))
   val rs2_data         = Output(UInt(conf.xlen.W))
   val alu_out          = Output(UInt(conf.xlen.W))
   val ctrl_wb_sel      = Output(UInt(WB_X.getWidth.W))
   val ctrl_rf_wen      = Output(Bool())
   val ctrl_mem_val     = Output(Bool())
   val ctrl_mem_fcn     = Output(UInt(M_X.getWidth.W)) 
   val ctrl_mem_typ     = Output(UInt(MT_X.getWidth.W))
   val ctrl_csr_cmd     = Output(UInt(CSR.N.getWidth.W))
   val exception        = Output(UInt(EXC_NORMAL.getWidth.W))
   val inst             = Output(UInt(conf.xlen.W))
}

class EXUToIFUOut (implicit val conf: Config) extends Bundle() {
   val exe_brjmp_target    =   Output(UInt(conf.xprlen.W))
   val exe_jump_reg_target =   Output(UInt(conf.xprlen.W))
   val btb_req             =   Output(new BTBUpdateReq)
   val ras_req             =   Output(new RASUpdateReq)
   val target              = Output(UInt(conf.xprlen.W))
}
class EXUToCTLIO (implicit val conf: Config) extends Bundle() {
   val alu_out          = Output(UInt(conf.xlen.W))
   val pc               = Output(UInt(conf.xprlen.W))
   val wbaddr           = Output(UInt(5.W))
   val inst_is_load     = Output(Bool())
   val ctrl_rf_wen      = Output(Bool())
   val is_csr           = Output(Bool())
   val br_type          = Output(UInt(BR_N.getWidth.W)) // for debug use
   val ctrl_exe_pc_sel  = Output(UInt(PC_4.getWidth.W))
   val redirect_type    = Output(UInt(RD_X.getWidth.W))
   val should_redirect    = Output(Bool())
   
}

class EXUDebugPort(implicit val conf: Config) extends Bundle() {

   val predict_wrong       = Output(UInt(conf.perfCountBits.W))
   val target_wrong        = Output(UInt(conf.perfCountBits.W))
   val br_wrong            = Output(UInt(conf.perfCountBits.W))
   val predict_hit         = Output(UInt(conf.perfCountBits.W))
   val predict_count       = Output(UInt(conf.perfCountBits.W))
}


class DpathIo(implicit val conf: Config) extends Bundle() 
{
   val dec_exe    = Flipped(new DecoupledIO(new DecPipeIO()))
   val exe_mem    = new DecoupledIO(new EXEPipeIO())
   val ctl        = new CtrlSignalIO()
   val ifu_out    = new EXUToIFUOut()
   val to_ctl     = new EXUToCTLIO()
   val debug      = Output(new EXUDebugPort())
}

class EXU(implicit val conf: Config) extends Module with HasBPUParams 
{
   val io = IO(new DpathIo())
   io := DontCare
   io.dec_exe.ready := true.B
   val alu_op1 = io.dec_exe.bits.op1_data.asUInt
   val alu_op2 = io.dec_exe.bits.op2_data.asUInt

   // ALU
   val alu_out   = Wire(UInt(conf.xprlen.W))
   val alu_shamt = alu_op2(4,0).asUInt

   alu_out := MuxCase(0.U, Seq(
                  (io.dec_exe.bits.alu_fun === ALU_ADD)  -> (alu_op1 + alu_op2).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_SUB)  -> (alu_op1 - alu_op2).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_AND)  -> (alu_op1 & alu_op2).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_OR)   -> (alu_op1 | alu_op2).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_XOR)  -> (alu_op1 ^ alu_op2).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_SLT)  -> (alu_op1.asSInt < alu_op2.asSInt).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_SLTU) -> (alu_op1 < alu_op2).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_SLL)  -> ((alu_op1 << alu_shamt)(conf.xprlen-1, 0)).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_SRA)  -> (alu_op1.asSInt >> alu_shamt).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_SRL)  -> (alu_op1 >> alu_shamt).asUInt,
                  (io.dec_exe.bits.alu_fun === ALU_COPY_1)-> alu_op1,
                  (io.dec_exe.bits.alu_fun === ALU_COPY_2)-> alu_op2
                  ))

   // Branch/Jump Target Calculation
   val pc_plus4    = ( io.dec_exe.bits.pc + 4.U)(conf.xprlen-1,0)
   val brjmp_offset                 = io.dec_exe.bits.op2_data
   val exe_brjmp_target             = io.dec_exe.bits.pc + brjmp_offset
   val exe_jump_reg_target          = (alu_op1 + alu_op2)(conf.xprlen-1,0)
   
   io.ifu_out.exe_brjmp_target      := exe_brjmp_target
   io.ifu_out.exe_jump_reg_target   := exe_jump_reg_target


   ////// Branch Logic
   val br_eq    = (io.dec_exe.bits.op1_data     ===  io.dec_exe.bits.rs2_data)
   val br_lt    = (io.dec_exe.bits.op1_data.asSInt < io.dec_exe.bits.rs2_data.asSInt) 
   val br_ltu   = (io.dec_exe.bits.op1_data.asUInt < io.dec_exe.bits.rs2_data.asUInt)
  
   val exe_br_type = io.dec_exe.bits.br_type
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

   // val cond_sel = Mux(taken, PC_BRJMP, PC_4)

   val bpu_resp            =  io.dec_exe.bits.bpu_resp
   val br_pc_sel           = Mux(is_cond_br, PC_BRJMP, base_sel)
   val groupIdx            = getGroupOffset(io.dec_exe.bits.pc)
   val target              = Mux(taken && is_cond_br || exe_br_type === BR_J,  exe_brjmp_target,
                              Mux(exe_br_type === BR_JR,   exe_jump_reg_target,
                              pc_plus4))
   val predict_wrong       = Mux(!taken && is_cond_br, bpu_resp.brIdx(groupIdx).asBool, 
                                  (!bpu_resp.brIdx(groupIdx).asBool || target =/= bpu_resp.target))
   val should_redirect     =  predict_wrong && (is_jump || is_cond_br) 

   val ctrl_exe_pc_sel     = Mux(io.ctl.pipeline_kill, PC_EXC,
                              Mux(exe_br_type === BR_N || !should_redirect, PC_4,br_pc_sel))
  

   io.ifu_out.btb_req.valid            := io.dec_exe.valid && exe_br_type =/= BR_N 
   io.ifu_out.btb_req.addr             := io.dec_exe.bits.pc
   io.ifu_out.btb_req.target           := target
   io.ifu_out.btb_req.taken            := taken
   io.ifu_out.btb_req.is_miss          := predict_wrong
   io.ifu_out.btb_req.redirect_type    := io.dec_exe.bits.redirect_type   

   io.ifu_out.ras_req.valid            := io.dec_exe.valid && io.dec_exe.bits.redirect_type === RD_RET 
   io.ifu_out.ras_req.addr             := io.dec_exe.bits.pc
   io.ifu_out.ras_req.target           := target
   io.ifu_out.ras_req.taken            := taken
   io.ifu_out.ras_req.is_miss          := predict_wrong
   io.ifu_out.ras_req.redirect_type    := io.dec_exe.bits.redirect_type
   io.ifu_out.target                   := target


   when (io.ctl.pipeline_kill){
      io.exe_mem.bits.pc_valid         := false.B
      io.exe_mem.bits.csr_inst         := 0.U
      io.exe_mem.bits.ctrl_rf_wen      := false.B
      io.exe_mem.bits.ctrl_mem_val     := false.B
      io.exe_mem.bits.ctrl_csr_cmd     := false.B
      io.exe_mem.valid                 := true.B
      io.exe_mem.bits.inst             := BUBBLE
   } .otherwise{
      // (1) exe_mem.ready = false -> exe_mem_reg == old  exe_mem_reg != io.exe_mem
      // (2) io.dec_exe.valid = false -> dec_exe_reg == old, exe_mem_reg == io.exe_mem
      io.dec_exe.ready              := io.exe_mem.ready
      io.exe_mem.valid              := true.B
      io.exe_mem.bits.pc            := io.dec_exe.bits.pc
      io.exe_mem.bits.pc_valid      := io.dec_exe.bits.pc_valid
      io.exe_mem.bits.csr_inst      := io.dec_exe.bits.inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
      io.exe_mem.bits.alu_out       := Mux((io.dec_exe.bits.ctrl_wb_sel === WB_PC4), pc_plus4, alu_out)
      io.exe_mem.bits.wbaddr        := io.dec_exe.bits.wbaddr
      io.exe_mem.bits.rs2_data      := io.dec_exe.bits.rs2_data
      io.exe_mem.bits.ctrl_rf_wen   := io.dec_exe.bits.ctrl_rf_wen
      io.exe_mem.bits.ctrl_mem_val  := io.dec_exe.bits.ctrl_mem_val
      io.exe_mem.bits.ctrl_mem_fcn  := io.dec_exe.bits.ctrl_mem_fcn
      io.exe_mem.bits.ctrl_mem_typ  := io.dec_exe.bits.ctrl_mem_typ
      io.exe_mem.bits.ctrl_wb_sel   := io.dec_exe.bits.ctrl_wb_sel
      io.exe_mem.bits.ctrl_csr_cmd  := io.dec_exe.bits.ctrl_csr_cmd
      io.exe_mem.bits.exception     := io.dec_exe.bits.exception
      io.exe_mem.bits.inst          := io.dec_exe.bits.inst
   }

   
   io.to_ctl.alu_out          := alu_out
   io.to_ctl.wbaddr           := io.dec_exe.bits.wbaddr
   io.to_ctl.ctrl_rf_wen      := io.dec_exe.bits.ctrl_rf_wen
   io.to_ctl.is_csr           := io.dec_exe.bits.ctrl_csr_cmd =/= CSR.N && io.dec_exe.bits.ctrl_csr_cmd =/= CSR.I
   io.to_ctl.inst_is_load     := io.dec_exe.bits.ctrl_mem_val && (io.dec_exe.bits.ctrl_mem_fcn === M_XRD)
   io.to_ctl.redirect_type    := io.dec_exe.bits.redirect_type
   io.to_ctl.ctrl_exe_pc_sel  := ctrl_exe_pc_sel
   io.to_ctl.should_redirect  := should_redirect


   /////////// DEBUG
   val perfCounters = RegInit(VecInit(Seq.fill(5)(0.U(conf.perfCountBits.W))))
   val Seq( predict_wrong_count, target_wrong_count, br_wrong_count ,predict_hit_count, predict_count ) = perfCounters

   val target_wrong = !bpu_resp.brIdx(0) || target =/= bpu_resp.target
   val br_wrong = predict_wrong && (is_cond_br)
   when(bpu_resp.valid && io.dec_exe.valid){
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
}