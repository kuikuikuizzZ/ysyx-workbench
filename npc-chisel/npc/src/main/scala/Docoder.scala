package npc

import chisel3._
import chisel3.util._

import npc.common.Instructions._
import npc.common._
import npc.Constants._

class DecPipeIO(implicit val conf: ysyx_24100012_Config) extends Bundle()
{
   val inst             = Output(UInt(conf.xlen.W))
   val pc               = Output(UInt(conf.xprlen.W))
   val wbaddr           = Output(UInt(5.W))
   val rs1_addr         = Output(UInt(5.W))
   val rs2_addr         = Output(UInt(5.W))
   val op1_data         = Output(UInt(conf.xprlen.W))
   val op2_data         = Output(UInt(conf.xprlen.W))
   val rs2_data         = Output(UInt(conf.xprlen.W))
   val br_type          = Output(UInt(BR_N.getWidth.W))
   val op2_sel          = Output(UInt())
   val alu_fun          = Output(UInt())
   val ctrl_wb_sel      = Output(UInt())
   val ctrl_rf_wen      = Output(Bool())
   val ctrl_mem_val     = Output(Bool())
   val ctrl_mem_fcn     = Output(UInt(M_X.getWidth.W)) 
   val ctrl_mem_typ     = Output(UInt(MT_X.getWidth.W))
   val ctrl_csr_cmd     = Output(UInt(CSR.N.getWidth.W))
}


class CtrlSignalIO(implicit val conf: ysyx_24100012_Config) extends Bundle() {
  val pc_sel              =   Input(UInt(PC_4.getWidth.W))
  val dec_stall           =   Input(Bool())
  val full_stall          =   Input(Bool())
  val pipeline_kill       =   Input(Bool())
  val if_kill             =   Input(Bool())
  val dec_kill            =   Input(Bool())
  val mem_exception       =   Input(Bool())
}

class CtrlDebugPort(implicit val conf: ysyx_24100012_Config) extends Bundle()
{ 
   val csrCount      = Output(UInt(conf.perfCountBits.W))   
   val storeCount    = Output(UInt(conf.perfCountBits.W)) 
   val loadCount     = Output(UInt(conf.perfCountBits.W))  
   val itypeCount    = Output(UInt(conf.perfCountBits.W)) 
   val rtypeCount    = Output(UInt(conf.perfCountBits.W)) 
   val jtypeCount    = Output(UInt(conf.perfCountBits.W)) 
   val utypeCount    = Output(UInt(conf.perfCountBits.W)) 
   val otherCount    = Output(UInt(conf.perfCountBits.W)) 
}

class CpathIo(implicit val conf: ysyx_24100012_Config) extends Bundle()
{
   val icache_valid  =  Input(Bool())
   val dec_reg       =  Flipped(new RegFilePipeIn())
   val ifu_pipe      =  Flipped(new DecoupledIO(new IFUPipeIO))
   val dec_exe       =  new DecoupledIO( new DecPipeIO)
   val reg_in        =  Flipped(new RegFileOut())
   val ctl_sign       =  Flipped(new CtrlSignalIO)
   val ctl_lsu       =  new CtlToLSUlIO
   val lsu_ctl       =  Flipped(new LSUTOCtlIO)
   val exe_ctl       = Flipped(new ToCTLIO())
   val mem_wbdata    =  Input(UInt(conf.xlen.W))
   val wb_wbdata     =  Input(UInt(conf.xlen.W))
   val debug         =  new CtrlDebugPort
}

class ysyx_24100012_Decoder(implicit val conf: ysyx_24100012_Config) extends Module
{
   val io = IO(new CpathIo())
   io := DontCare
   val dec_reg_inst = io.ifu_pipe.bits.inst
   val dec_reg_pc = io.ifu_pipe.bits.pc
   io.ifu_pipe.ready := true.B
   // Control Signals
   val csignals =
      ListLookup(dec_reg_inst,
                             List(N, BR_N  , OP1_X , OP2_X    , OEN_0, OEN_0, ALU_X   , WB_X  ,  REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
               Array(       /* val  |  BR  |  op1  |   op2     |  R1  |  R2  |  ALU    |  wb   | rf   | mem  | mem  | mask | csr | fence.i */
                            /* inst | type |   sel |    sel    |  oen |  oen |   fcn   |  sel  | wen  |  en  |  wr  | type | cmd |         */
                  LW     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_W, CSR.N, N),
                  LB     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_B, CSR.N, N),
                  LBU    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_BU,CSR.N, N),
                  LH     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_H, CSR.N, N),
                  LHU    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_HU,CSR.N, N),
                  SW     -> List(Y, BR_N  , OP1_RS1, OP2_STYPE , OEN_1, OEN_1, ALU_ADD , WB_X  , REN_0, MEN_1, M_XWR, MT_W, CSR.N, N),
                  SB     -> List(Y, BR_N  , OP1_RS1, OP2_STYPE , OEN_1, OEN_1, ALU_ADD , WB_X  , REN_0, MEN_1, M_XWR, MT_B, CSR.N, N),
                  SH     -> List(Y, BR_N  , OP1_RS1, OP2_STYPE , OEN_1, OEN_1, ALU_ADD , WB_X  , REN_0, MEN_1, M_XWR, MT_H, CSR.N, N),

                  AUIPC  -> List(Y, BR_N  , OP1_PC , OP2_UTYPE , OEN_0, OEN_0, ALU_ADD   ,WB_ALU,REN_1, MEN_0, M_X , MT_X,  CSR.N, N),
                  LUI    -> List(Y, BR_N  , OP1_X  , OP2_UTYPE , OEN_0, OEN_0, ALU_COPY_2,WB_ALU,REN_1, MEN_0, M_X , MT_X,  CSR.N, N),

                  ADDI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  ANDI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_AND , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  ORI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_OR  , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  XORI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_XOR , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLTI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SLT , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLTIU  -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SLTU, WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLLI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SLL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SRAI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SRA , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SRLI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SRL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),

                  SLL    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SLL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  ADD    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_ADD , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SUB    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SUB , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLT    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SLT , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLTU   -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SLTU, WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  AND    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_AND , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  OR     -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_OR  , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  XOR    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_XOR , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SRA    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SRA , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SRL    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SRL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),

                  JAL    -> List(Y, BR_J  , OP1_RS1, OP2_UJTYPE, OEN_0, OEN_0, ALU_X   , WB_PC4, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  JALR   -> List(Y, BR_JR , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_X   , WB_PC4, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  BEQ    -> List(Y, BR_EQ , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BNE    -> List(Y, BR_NE , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BGE    -> List(Y, BR_GE , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BGEU   -> List(Y, BR_GEU, OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BLT    -> List(Y, BR_LT , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BLTU   -> List(Y, BR_LTU, OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),

                  CSRRWI -> List(Y, BR_N  , OP1_IMZ, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.W, N),
                  CSRRSI -> List(Y, BR_N  , OP1_IMZ, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.S, N),
                  CSRRW  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.W, N),
                  CSRRS  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.S, N),
                  CSRRC  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.C, N),
                  CSRRCI -> List(Y, BR_N  , OP1_IMZ, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.C, N),

                  ECALL  -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  MRET   -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  DRET   -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  EBREAK -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  WFI    -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N), // implemented as a NOP

                  FENCE_I-> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, Y),
                  // kill pipeline and refetch instructions since the pipeline will be holding stall instructions.
                  FENCE  -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_1, M_X  , MT_X, CSR.N, N)
                  // we are already sequentially consistent, so no need to honor the fence instruction
                  ))

   // Put these control signals in variables
   val (cs_val_inst: Bool) :: (cs_br_type:UInt) :: (cs_op1_sel:UInt) :: (cs_op2_sel:UInt) :: (cs_rs1_oen: Bool) :: (cs_rs2_oen: Bool) :: cs0 = csignals
   val cs_alu_fun :: cs_wb_sel :: (cs_rf_wen: Bool) :: (cs_mem_en: Bool) :: cs_mem_fcn :: cs_msk_sel :: cs_csr_cmd :: (cs_fencei: Bool) :: Nil = cs0

              
   
   /////// Register File Interface //////
   val dec_rs1_addr = dec_reg_inst(RS1_MSB, RS1_LSB)
   val dec_rs2_addr = dec_reg_inst(RS2_MSB, RS2_LSB)
   val dec_wbaddr   = dec_reg_inst(RD_MSB, RD_LSB)
   val rf_rs1_data = io.reg_in.rs1_data
   val rf_rs2_data = io.reg_in.rs2_data
   io.dec_reg.rs1_addr := dec_rs1_addr
   io.dec_reg.rs2_addr := dec_rs2_addr
   
   ////// Branch Logic
   val br_eq  = (io.reg_in.rs1_data === io.reg_in.rs2_data)
   val br_lt  = (io.reg_in.rs1_data.asSInt < io.reg_in.rs2_data.asSInt) 
   val br_ltu = (io.reg_in.rs1_data.asUInt < io.reg_in.rs2_data.asUInt)
   val pipeline_kill = Wire(Bool())

   val ctrl_exe_pc_sel = Mux(pipeline_kill         , PC_EXC,
                         Mux(cs_br_type === BR_N  , PC_4,
                         Mux(cs_br_type === BR_NE , Mux(!br_eq,  PC_BRJMP, PC_4),
                         Mux(cs_br_type === BR_EQ , Mux( br_eq,  PC_BRJMP, PC_4),
                         Mux(cs_br_type === BR_GE , Mux(!br_lt,  PC_BRJMP, PC_4),
                         Mux(cs_br_type === BR_GEU, Mux(!br_ltu, PC_BRJMP, PC_4),
                         Mux(cs_br_type === BR_LT , Mux( br_lt,  PC_BRJMP, PC_4),
                         Mux(cs_br_type === BR_LTU, Mux( br_ltu, PC_BRJMP, PC_4),
                         Mux(cs_br_type === BR_J  , PC_BRJMP,
                         Mux(cs_br_type === BR_JR , PC_JALR,
                                                            PC_4
                     ))))))))))   

   val ifkill  = (ctrl_exe_pc_sel =/= PC_4) || !io.icache_valid || cs_fencei || RegNext(cs_fencei)
   val deckill = (ctrl_exe_pc_sel =/= PC_4)

   // Exception Handling ---------------------

   // io.ctl.pipeline_kill := (io.dat.csr_eret || io.ctl.mem_exception)
   // val dec_exception = (!cs_val_inst && io.icache_valid)
   val dec_exception = false.B
   val exe_reg_exception   = RegInit(false.B)
   val mem_exception = RegNext(exe_reg_exception)
   io.ctl_lsu.mem_exception := mem_exception
   pipeline_kill := mem_exception 
   
   // Stall Signal Logic --------------------
   
   val stall   = Wire(Bool())

   val dec_rs1_oen  = Mux(deckill, false.B, cs_rs1_oen)
   val dec_rs2_oen  = Mux(deckill, false.B, cs_rs2_oen)

   val exe_reg_wbaddr      = Reg(UInt())
   val mem_reg_wbaddr      = Reg(UInt())
   val wb_reg_wbaddr       = Reg(UInt())
   val exe_reg_ctrl_rf_wen = RegInit(false.B)
   val mem_reg_ctrl_rf_wen = RegInit(false.B)
   val wb_reg_ctrl_rf_wen  = RegInit(false.B)

   val exe_reg_is_csr = RegInit(false.B)

   // TODO rename stall==hazard_stall full_stall == cmiss_stall
   val full_stall = Wire(Bool())
   when (!stall && !full_stall)
   {
      when (deckill)
      {
         exe_reg_wbaddr      := 0.U
         exe_reg_ctrl_rf_wen := false.B
         exe_reg_is_csr      := false.B
         exe_reg_exception   := false.B
      }
      .otherwise
      {
         exe_reg_wbaddr      := dec_wbaddr
         exe_reg_ctrl_rf_wen := cs_rf_wen
         exe_reg_is_csr      := cs_csr_cmd =/= CSR.N && cs_csr_cmd =/= CSR.I
         exe_reg_exception   := dec_exception
      }
   }
   .elsewhen (stall && !full_stall)
   {
      // kill exe stage
      exe_reg_wbaddr      := 0.U
      exe_reg_ctrl_rf_wen := false.B
      exe_reg_is_csr      := false.B
      exe_reg_exception   := false.B
   }

   mem_reg_wbaddr      := exe_reg_wbaddr
   wb_reg_wbaddr       := mem_reg_wbaddr
   mem_reg_ctrl_rf_wen := exe_reg_ctrl_rf_wen
   wb_reg_ctrl_rf_wen  := mem_reg_ctrl_rf_wen

   val exe_inst_is_load = RegInit(false.B)

   when (!full_stall)
   {
      exe_inst_is_load := cs_mem_en && (cs_mem_fcn === M_XRD)
   }

   
   if (conf.USE_FULL_BYPASSING)
   {
      // stall for load-use hazard
      stall := ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs1_addr) && (exe_reg_wbaddr =/= 0.U) && dec_rs1_oen) ||
               ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs2_addr) && (exe_reg_wbaddr =/= 0.U) && dec_rs2_oen) ||
               (exe_reg_is_csr)
   }
   else
   {
      // stall for all hazards
      stall := ((exe_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) && exe_reg_ctrl_rf_wen && dec_rs1_oen) ||
               ((mem_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) && mem_reg_ctrl_rf_wen && dec_rs1_oen) ||
               ((wb_reg_wbaddr  === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) &&  wb_reg_ctrl_rf_wen && dec_rs1_oen) ||
               ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && exe_reg_ctrl_rf_wen && dec_rs2_oen) ||
               ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && mem_reg_ctrl_rf_wen && dec_rs2_oen) ||
               ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) &&  wb_reg_ctrl_rf_wen && dec_rs2_oen) ||
               ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs1_addr) && (exe_reg_wbaddr =/= 0.U) && dec_rs1_oen) ||
               ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs2_addr) && (exe_reg_wbaddr =/= 0.U) && dec_rs2_oen) ||
               ((exe_reg_is_csr))
   }
   // stall full pipeline on D$ miss
   val dmem_val   = io.lsu_ctl.ctrl_mem_val
   full_stall    := !io.icache_valid || !((dmem_val && io.lsu_ctl.resp_valid) || !dmem_val)

   io.ctl_sign.pc_sel := ctrl_exe_pc_sel
   io.ctl_sign.if_kill := ifkill
   io.ctl_sign.dec_kill := deckill
   io.ctl_sign.dec_stall := stall
   io.ctl_sign.full_stall := full_stall
   io.ctl_sign.pipeline_kill := pipeline_kill
   io.ctl_sign.mem_exception := mem_exception

   // immediates
   val imm_i = dec_reg_inst(31, 20) 
   val imm_s = Cat(dec_reg_inst(31, 25), dec_reg_inst(11,7))
   val imm_b = Cat(dec_reg_inst(31), dec_reg_inst(7), dec_reg_inst(30,25), dec_reg_inst(11,8))
   val imm_u = dec_reg_inst(31, 12)
   val imm_j = Cat(dec_reg_inst(31), dec_reg_inst(19,12), dec_reg_inst(20), dec_reg_inst(30,21))
   val imm_z = Cat(Fill(27,0.U), dec_reg_inst(19,15))

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(20,imm_i(11)), imm_i)
   val imm_s_sext = Cat(Fill(20,imm_s(11)), imm_s)
   val imm_b_sext = Cat(Fill(19,imm_b(11)), imm_b, 0.U)
   val imm_u_sext = Cat(imm_u, Fill(12,0.U))
   val imm_j_sext = Cat(Fill(11,imm_j(19)), imm_j, 0.U)

   // Operand 2 Mux
   val alu_op2 = MuxCase(0.U, Array(
               (cs_op2_sel === OP2_RS2)    -> rf_rs2_data,
               (cs_op2_sel === OP2_ITYPE)  -> imm_i_sext,
               (cs_op2_sel === OP2_STYPE)  -> imm_s_sext,
               (cs_op2_sel === OP2_SBTYPE) -> imm_b_sext,
               (cs_op2_sel === OP2_UTYPE)  -> imm_u_sext,
               (cs_op2_sel === OP2_UJTYPE) -> imm_j_sext
               )).asUInt
   // val exe_alu_out  = Wire(UInt(conf.xprlen.W))
   // val mem_wbdata   = Wire(UInt(conf.xprlen.W))

   val op1_data = Wire(UInt(conf.xprlen.W))
   val op2_data = Wire(UInt(conf.xprlen.W))
   val rs2_data = Wire(UInt(conf.xprlen.W))

   if (conf.USE_FULL_BYPASSING){
      // roll the OP1 mux into the bypass mux logic
      op1_data := MuxCase(rf_rs1_data, Array(
                           ((cs_op1_sel === OP1_IMZ)) -> imm_z,
                           ((cs_op1_sel === OP1_PC)) -> dec_reg_pc,
                           ((exe_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) && exe_reg_ctrl_rf_wen) -> io.exe_ctl.alu_out,
                           ((mem_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) && mem_reg_ctrl_rf_wen) -> io.mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) &&  wb_reg_ctrl_rf_wen) -> io.wb_wbdata
                           ))

      op2_data := MuxCase(alu_op2, Array(
                           ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && exe_reg_ctrl_rf_wen && (cs_op2_sel === OP2_RS2)) -> io.exe_ctl.alu_out,
                           ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && mem_reg_ctrl_rf_wen && (cs_op2_sel === OP2_RS2)) -> io.mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) &&  wb_reg_ctrl_rf_wen && (cs_op2_sel === OP2_RS2)) -> io.wb_wbdata
                           ))

      rs2_data := MuxCase(rf_rs2_data, Array(
                           ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && exe_reg_ctrl_rf_wen) -> io.exe_ctl.alu_out,
                           ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && mem_reg_ctrl_rf_wen) -> io.mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) &&  wb_reg_ctrl_rf_wen) -> io.wb_wbdata
                           ))
   } else{
      // Rely only on control interlocking to resolve hazards
      op1_data := MuxCase(rf_rs1_data, Array(
                          ((cs_op1_sel === OP1_IMZ)) -> imm_z,
                          ((cs_op1_sel === OP1_PC))  -> dec_reg_pc
                          ))
      rs2_data := rf_rs2_data
      op2_data := alu_op2
   }

   when ((stall && !full_stall) || pipeline_kill)
   {
      // (kill exe stage)
      // insert NOP (bubble) into Execute stage on front-end stall (e.g., hazard clearing)
      io.dec_exe.valid         := false.B
      io.dec_exe.bits.inst          := BUBBLE
      io.dec_exe.bits.wbaddr        := 0.U
      io.dec_exe.bits.ctrl_rf_wen   := false.B
      io.dec_exe.bits.ctrl_mem_val  := false.B
      io.dec_exe.bits.ctrl_mem_fcn  := M_X
      io.dec_exe.bits.ctrl_csr_cmd  := CSR.N
      io.dec_exe.bits.br_type       := BR_N
   }
   .elsewhen(!stall && !full_stall)
   {
      // no stalling...
      io.dec_exe.bits.pc            := dec_reg_pc
      io.dec_exe.bits.rs1_addr      := dec_rs1_addr
      io.dec_exe.bits.rs2_addr      := dec_rs2_addr
      io.dec_exe.bits.op1_data      := op1_data
      io.dec_exe.bits.op2_data      := op2_data
      io.dec_exe.bits.rs2_data      := rs2_data
      io.dec_exe.bits.op2_sel       := cs_op2_sel
      io.dec_exe.bits.alu_fun       := cs_alu_fun
      io.dec_exe.bits.ctrl_wb_sel   := cs_wb_sel

      when (deckill)
      {
         io.dec_exe.valid         := false.B
         io.dec_exe.bits.inst          := BUBBLE
         io.dec_exe.bits.wbaddr        := 0.U
         io.dec_exe.bits.ctrl_rf_wen   := false.B
         io.dec_exe.bits.ctrl_mem_val  := false.B
         io.dec_exe.bits.ctrl_mem_fcn  := M_X
         io.dec_exe.bits.ctrl_csr_cmd  := CSR.N
         io.dec_exe.bits.br_type  := BR_N
      }
      .otherwise
      {
         io.dec_exe.valid              := io.ifu_pipe.valid
         io.dec_exe.bits.inst          := dec_reg_inst
         io.dec_exe.bits.wbaddr        := dec_wbaddr
         io.dec_exe.bits.ctrl_rf_wen   := cs_rf_wen
         io.dec_exe.bits.ctrl_mem_val  := cs_mem_en
         io.dec_exe.bits.ctrl_mem_fcn  := cs_mem_fcn
         io.dec_exe.bits.ctrl_mem_typ  := cs_msk_sel
         io.dec_exe.bits.ctrl_csr_cmd  := cs_csr_cmd
         io.dec_exe.bits.br_type  := cs_br_type
      }
   }

   /////////   Debug Signals
   val perfCounters = RegInit(VecInit(Seq.fill(8)(0.U(conf.perfCountBits.W))))
   val Seq( loadCount, storeCount, jtypeCount, utypeCount, itypeCount, 
            rtypeCount, csrCount, otherCount ) = perfCounters
  // 加载指令检测
   val isLoad = dec_reg_inst === LB || dec_reg_inst === LH || dec_reg_inst === LW || 
                  dec_reg_inst === LBU || dec_reg_inst === LHU
   
   // 存储指令检测
   val isStore = dec_reg_inst === SB || dec_reg_inst === SH || dec_reg_inst === SW
   
   // 分支指令检测
   val isBranch = dec_reg_inst === BEQ || dec_reg_inst === BNE || 
                  dec_reg_inst === BLT || dec_reg_inst === BGE || 
                  dec_reg_inst === BLTU || dec_reg_inst === BGEU
   
   // 跳转指令检测
   val isJump = dec_reg_inst === JAL || dec_reg_inst === JALR
   
   // I型指令检测
   val isIType = dec_reg_inst === ADDI || dec_reg_inst === ANDI || dec_reg_inst === ORI || 
                  dec_reg_inst === XORI || dec_reg_inst === SLTI || dec_reg_inst === SLTIU || 
                  dec_reg_inst === SLLI || dec_reg_inst === SRAI || dec_reg_inst === SRLI
   
   // R型指令检测
   val isRType = dec_reg_inst === ADD || dec_reg_inst === SUB || dec_reg_inst === SLL || 
                  dec_reg_inst === SLT || dec_reg_inst === SLTU || dec_reg_inst === XOR || 
                  dec_reg_inst === SRL || dec_reg_inst === SRA || dec_reg_inst === OR || 
                  dec_reg_inst === AND
   
   // CSR指令检测
   val isCSR = dec_reg_inst === CSRRSI ||dec_reg_inst === CSRRCI ||dec_reg_inst === CSRRW || dec_reg_inst === CSRRS || 
         dec_reg_inst === CSRRC || dec_reg_inst === ECALL || dec_reg_inst === MRET ||   dec_reg_inst === DRET || 
         dec_reg_inst === EBREAK ||dec_reg_inst === WFI  || dec_reg_inst === FENCE_I || dec_reg_inst === FENCE  

   val isUtype = dec_reg_inst === LUI || dec_reg_inst === AUIPC
   when(io.ifu_pipe.valid){
      when(isLoad) {
         loadCount := loadCount + 1.U
      }.elsewhen(isStore) {
         storeCount := storeCount + 1.U
      }.elsewhen(isBranch) {
         jtypeCount := jtypeCount + 1.U
      }.elsewhen(isJump) {
         jtypeCount := jtypeCount + 1.U
      }.elsewhen(isIType) {
         itypeCount := itypeCount + 1.U
      }.elsewhen(isRType) {
         rtypeCount := rtypeCount + 1.U
      }.elsewhen(isCSR) {
         csrCount := csrCount + 1.U
      } .elsewhen(isUtype){
         utypeCount := utypeCount + 1.U
      }.otherwise {
         otherCount := otherCount + 1.U
      }
   }

   io.debug.csrCount    := csrCount      
   io.debug.storeCount  := storeCount  
   io.debug.loadCount   := loadCount    
   io.debug.itypeCount  := itypeCount  
   io.debug.rtypeCount  := rtypeCount  
   io.debug.jtypeCount  := jtypeCount  
   io.debug.utypeCount  := utypeCount
   io.debug.otherCount  := otherCount
}
