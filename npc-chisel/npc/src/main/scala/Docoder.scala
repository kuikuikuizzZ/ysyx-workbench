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
   val dec_reg    =  Flipped(new RegFilePipeIn())
   val ifu_pipe   =  Flipped(new DecoupledIO(new IFUPipeIO))
   val dec_exe    =  new DecoupledIO( new DecPipeIO)
   val reg_in     =  Flipped(new RegFileOut())
   val ifu_out    =  Flipped(new InstFetchIn)
   val debug      =  new CtrlDebugPort
}

class ysyx_24100012_Decoder(implicit val conf: ysyx_24100012_Config) extends Module
{
   val io = IO(new CpathIo())
   io := DontCare
   val if_inst = io.ifu_pipe.bits.inst
   val if_pc = io.ifu_pipe.bits.pc
   // Control Signals
   val csignals =
      ListLookup(if_inst,
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
   val rs1_addr = if_inst(RS1_MSB, RS1_LSB)
   val rs2_addr = if_inst(RS2_MSB, RS2_LSB)
   val wbaddr   = if_inst(RD_MSB, RD_LSB)
   val rf_rs1_data = io.reg_in.rs1_data
   val rf_rs2_data = io.reg_in.rs2_data
   io.dec_reg.rs1_addr := rs1_addr
   io.dec_reg.rs2_addr := rs2_addr
   
   ////// Branch Logic
   val br_eq  = (io.reg_in.rs1_data === io.reg_in.rs2_data)
   val br_lt  = (io.reg_in.rs1_data.asSInt < io.reg_in.rs2_data.asSInt) 
   val br_ltu = (io.reg_in.rs1_data.asUInt < io.reg_in.rs2_data.asUInt)
   val pipeline_kill = false.B
   val full_stall    = false.B
   val dec_stall     = false.B
   
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
   // val ifkill  = (ctrl_exe_pc_sel =/= PC_4) || !io.imem.resp.valid || cs_fencei || RegNext(cs_fencei)
   // val deckill = (ctrl_exe_pc_sel =/= PC_4)    
   val ifkill  = false.B
   val deckill = false.B
   
   io.ifu_out.pc_sel := ctrl_exe_pc_sel
   io.ifu_out.if_kill := ifkill
   io.ifu_out.dec_kill := deckill
   io.ifu_out.dec_stall := dec_stall
   io.ifu_out.full_stall := full_stall
   io.ifu_out.pipeline_kill := pipeline_kill

   // immediates
   val imm_i = if_inst(31, 20) 
   val imm_s = Cat(if_inst(31, 25), if_inst(11,7))
   val imm_b = Cat(if_inst(31), if_inst(7), if_inst(30,25), if_inst(11,8))
   val imm_u = if_inst(31, 12)
   val imm_j = Cat(if_inst(31), if_inst(19,12), if_inst(20), if_inst(30,21))
   val imm_z = Cat(Fill(27,0.U), if_inst(19,15))

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(20,imm_i(11)), imm_i)
   val imm_s_sext = Cat(Fill(20,imm_s(11)), imm_s)
   val imm_b_sext = Cat(Fill(19,imm_b(11)), imm_b, 0.U)
   val imm_u_sext = Cat(imm_u, Fill(12,0.U))
   val imm_j_sext = Cat(Fill(11,imm_j(19)), imm_j, 0.U)


   // val alu_op1 = MuxCase(0.U, Seq(
   //             (cs_op1_sel === OP1_RS1) -> rf_rs1_data,
   //             (cs_op1_sel === OP1_IMU) -> imm_u_sext,
   //             (cs_op1_sel === OP1_IMZ) -> imm_z
   //             )).asUInt

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
      // Rely only on control interlocking to resolve hazards
      op1_data := MuxCase(rf_rs1_data, Array(
                          ((cs_op1_sel === OP1_IMZ)) -> imm_z,
                          ((cs_op1_sel === OP1_PC))  -> if_pc
                          ))
      rs2_data := rf_rs2_data
      op2_data := alu_op2
   } else{
      // Rely only on control interlocking to resolve hazards
      op1_data := MuxCase(rf_rs1_data, Array(
                          ((cs_op1_sel === OP1_IMZ)) -> imm_z,
                          ((cs_op1_sel === OP1_PC))  -> if_pc
                          ))
      rs2_data := rf_rs2_data
      op2_data := alu_op2
   }

   io.dec_exe.valid              := io.ifu_pipe.valid
   io.dec_exe.bits.inst          := if_inst         
   io.dec_exe.bits.pc            := if_pc
   io.dec_exe.bits.wbaddr        := wbaddr
   io.dec_exe.bits.rs1_addr      := rs1_addr
   io.dec_exe.bits.rs2_addr      := rs2_addr
   io.dec_exe.bits.op1_data      := op1_data
   io.dec_exe.bits.op2_data      := op2_data
   io.dec_exe.bits.rs2_data      := rf_rs2_data
   io.dec_exe.bits.op2_sel       := cs_op2_sel
   io.dec_exe.bits.alu_fun       := cs_alu_fun
   io.dec_exe.bits.ctrl_wb_sel   := cs_wb_sel
   io.dec_exe.bits.ctrl_rf_wen   := cs_rf_wen
   io.dec_exe.bits.ctrl_mem_val  := cs_mem_en
   io.dec_exe.bits.ctrl_mem_fcn  := cs_mem_fcn
   io.dec_exe.bits.ctrl_mem_typ  := cs_msk_sel 
   io.dec_exe.bits.ctrl_csr_cmd  := cs_csr_cmd
   io.dec_exe.bits.br_type       := cs_br_type

   // // Set the data-path control signals
   // cs_op1_sel       :=      cs_op1_sel
   // cs_op2_sel       :=      cs_op2_sel
   // io.ctl.alu_fun       :=      cs_alu_fun
   // io.ctl.br_type       :=      cs_br_type

   // val mem_en            =       Mux(io.ifu_valid, cs_mem_en, MEN_0)  
   // io.ctl_lsu.mem_en    :=       mem_en
   // io.ctl_lsu.mem_fcn   :=       cs_mem_fcn
   // io.ctl_lsu.msk_sel   :=       cs_msk_sel
   // io.ctl_wb.exception  :=       io.ctl.exception
   
   // io.finish := Mux(cs_mem_en, io.ls_valid, io.ifu_valid) 

   // io.ctl_wb.rf_wen     := Mux(cs_mem_en, 
   //                            Mux( io.ls_valid  , cs_rf_wen, REN_0),
   //                            Mux( io.ifu_valid , cs_rf_wen, REN_0))  
   // io.ctl_wb.wb_sel     :=       cs_wb_sel
   
   // // convert CSR instructions with raddr1 == 0 to read-only CSR commands
   // val csr_ren = (cs_csr_cmd === CSR.S || cs_csr_cmd === CSR.C) && rs1_addr === 0.U
   // val csr_cmd = Mux(csr_ren, CSR.R, cs_csr_cmd)

   // // io.ctl.csr_cmd  := Mux(stall, CSR.N, csr_cmd)
   // io.ctl.csr_cmd := csr_cmd


   
   // Exception Handling ---------------------
   // We only need to check if the instruction is illegal (or unsupported)
   // or if the CSR file wants us to be interrupted.
   // Other exceptions are detected later in the pipeline by passing the
   // instruction to the CSR File and letting it redirect the PC as it sees
   // fit.
   // io.ctl.exception := (!cs_val_inst && io.ifu_valid) 
   // io.pipeline_kill :=  (!cs_val_inst ) 


   /////////   Debug Signals
   val perfCounters = RegInit(VecInit(Seq.fill(8)(0.U(conf.perfCountBits.W))))
   val Seq( loadCount, storeCount, jtypeCount, utypeCount, itypeCount, 
            rtypeCount, csrCount, otherCount ) = perfCounters
  // 加载指令检测
   val isLoad = if_inst === LB || if_inst === LH || if_inst === LW || 
                  if_inst === LBU || if_inst === LHU
   
   // 存储指令检测
   val isStore = if_inst === SB || if_inst === SH || if_inst === SW
   
   // 分支指令检测
   val isBranch = if_inst === BEQ || if_inst === BNE || 
                  if_inst === BLT || if_inst === BGE || 
                  if_inst === BLTU || if_inst === BGEU
   
   // 跳转指令检测
   val isJump = if_inst === JAL || if_inst === JALR
   
   // I型指令检测
   val isIType = if_inst === ADDI || if_inst === ANDI || if_inst === ORI || 
                  if_inst === XORI || if_inst === SLTI || if_inst === SLTIU || 
                  if_inst === SLLI || if_inst === SRAI || if_inst === SRLI
   
   // R型指令检测
   val isRType = if_inst === ADD || if_inst === SUB || if_inst === SLL || 
                  if_inst === SLT || if_inst === SLTU || if_inst === XOR || 
                  if_inst === SRL || if_inst === SRA || if_inst === OR || 
                  if_inst === AND
   
   // CSR指令检测
   val isCSR = if_inst === CSRRSI ||if_inst === CSRRCI ||if_inst === CSRRW || if_inst === CSRRS || 
         if_inst === CSRRC || if_inst === ECALL || if_inst === MRET ||   if_inst === DRET || 
         if_inst === EBREAK ||if_inst === WFI  || if_inst === FENCE_I || if_inst === FENCE  

   val isUtype = if_inst === LUI || if_inst === AUIPC
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
