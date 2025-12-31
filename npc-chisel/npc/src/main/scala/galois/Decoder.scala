package npc.galois

import chisel3._
import chisel3.util._

import npc.common.Instructions._
import npc.common._
import npc.common.Constants._
import npc._


class CtrlSignalIO(implicit val conf: Config) extends Bundle() {
  val exe_pc_sel           =   Input(UInt(PC_4.getWidth.W))
  val pipeline_kill        =   Input(Bool())
  val if_kill              =   Input(Bool())
  val dec_kill             =   Input(Bool())
  val fencei               =   Input(Bool())
}

class CtrlDebugPort(implicit val conf: Config) extends Bundle()
{ 
   val csrCount      = Output(UInt(conf.perfCountBits.W))   
   val storeCount    = Output(UInt(conf.perfCountBits.W)) 
   val loadCount     = Output(UInt(conf.perfCountBits.W))  
   val itypeCount    = Output(UInt(conf.perfCountBits.W)) 
   val rtypeCount    = Output(UInt(conf.perfCountBits.W)) 
   val jtypeCount    = Output(UInt(conf.perfCountBits.W)) 
   val btypeCount    = Output(UInt(conf.perfCountBits.W)) 
   val utypeCount    = Output(UInt(conf.perfCountBits.W)) 
   val otherCount    = Output(UInt(conf.perfCountBits.W)) 
}

class DecodeIO(implicit conf: Config) extends Bundle()
{
   val ifu_dec    = Flipped(DecoupledIO(new IFUPipeIO))
   val dec_rm     = DecoupledIO(new BlockLineIO())
   val redirect   = Input(Bool())
   val debug      = new CtrlDebugPort
}

class Decode (implicit val conf: Config) extends Module
{ 
   val io = IO(new DecodeIO())
   
   io.ifu_dec.ready := io.dec_rm.ready
   io.dec_rm.valid := io.ifu_dec.valid
   
   val decoderA = Module(new Decoder())
   val decoderB = Module(new Decoder())

   val instA = Mux(io.ifu_dec.fire,  io.ifu_dec.bits.instA, 0.U.asTypeOf( new IFUInstOut()))
   val instB = Mux(io.ifu_dec.fire,  io.ifu_dec.bits.instB, 0.U.asTypeOf( new IFUInstOut()))
   decoderA.io.valid       := io.ifu_dec.fire
   decoderA.io.inst        := instA
   decoderA.io.bpu_resp    := io.ifu_dec.bits.bpu_resp
   decoderA.io.exception   := io.ifu_dec.bits.exception
   decoderB.io.inst        := instB
   decoderB.io.valid       := io.ifu_dec.fire
   decoderB.io.bpu_resp    := io.ifu_dec.bits.bpu_resp
   decoderB.io.exception   := io.ifu_dec.bits.exception


   when(io.redirect){
      io.dec_rm.bits.instA   := 0.U.asTypeOf(new InstCtrlBlock())
      io.dec_rm.bits.instB   := 0.U.asTypeOf(new InstCtrlBlock())
   } .otherwise{
      io.dec_rm.bits.instA <> decoderA.io.dec_out
      io.dec_rm.bits.instB <> decoderB.io.dec_out
   }
   def reduceDebug( debugA : CtrlDebugPort, debugB : CtrlDebugPort): CtrlDebugPort = {
      val debug = WireInit(0.U.asTypeOf(new CtrlDebugPort()))
      debug.csrCount := debugA.csrCount + debugB.csrCount
      debug.storeCount := debugA.storeCount + debugB.storeCount
      debug.loadCount := debugA.loadCount + debugB.loadCount
      debug.itypeCount := debugA.itypeCount + debugB.itypeCount
      debug.rtypeCount := debugA.rtypeCount + debugB.rtypeCount
      debug.jtypeCount := debugA.jtypeCount + debugB.jtypeCount
      debug.btypeCount := debugA.btypeCount + debugB.btypeCount
      debug.utypeCount := debugA.utypeCount + debugB.utypeCount
      debug.otherCount := debugA.otherCount + debugB.otherCount
      debug
   }
   io.debug := reduceDebug(decoderA.io.debug, decoderB.io.debug)
}

class DecoderIo(implicit val conf: Config) extends Bundle()
{
   val valid            =  Input(Bool())
   val inst             =  Flipped(new IFUInstOut)
   val bpu_resp         =  Input(new BPUResp)
   val exception        =  Input(UInt(EXC_NORMAL.getWidth.W))
   val dec_out          =  Output(new InstCtrlBlock())
   val debug            =  new CtrlDebugPort
}

class Decoder(implicit val conf: Config) extends Module
{
   val io = IO(new DecoderIo())
   val dec_reg_inst = io.inst.inst
   val dec_reg_pc = io.inst.pc

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

                  CSRRW  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.W, N),
                  CSRRS  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.S, N),

                  ECALL  -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  MRET   -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  EBREAK -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),

                  FENCE_I-> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, Y),
                  // kill pipeline and refetch instructions since the pipeline will be holding stall instructions.
                  // FENCE  -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_1, M_X  , MT_X, CSR.N, N)
                  // we are already sequentially consistent, so no need to honor the fence instruction
                  ))

   // Put these control signals in variables
   val (cs_val_inst: Bool) :: (cs_br_type:UInt) :: (cs_op1_sel:UInt) :: (cs_op2_sel:UInt) :: (cs_rs1_oen: Bool) :: (cs_rs2_oen: Bool) :: cs0 = csignals
   val cs_alu_fun :: cs_wb_sel :: (cs_rf_wen: Bool) :: (cs_mem_en: Bool) :: cs_mem_fcn :: cs_msk_sel :: cs_csr_cmd :: (cs_fencei: Bool) :: Nil = cs0

   /////// Register File Interface //////
   val dec_rs1_addr = dec_reg_inst(RS1_MSB, RS1_LSB)
   val dec_rs2_addr = dec_reg_inst(RS2_MSB, RS2_LSB)
   val dec_wbaddr   = dec_reg_inst(RD_MSB, RD_LSB)
            
   
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

   val imm = MuxCase(0.U, Array(
            (cs_op2_sel === OP2_ITYPE)  -> imm_i_sext,
            (cs_op2_sel === OP2_STYPE)  -> imm_s_sext,
            (cs_op2_sel === OP2_SBTYPE) -> imm_b_sext,
            (cs_op2_sel === OP2_UTYPE)  -> imm_u_sext,
            (cs_op2_sel === OP2_UJTYPE) -> imm_j_sext
            )).asUInt

   val is_cond_br = cs_br_type === BR_NE || cs_br_type === BR_EQ ||
                  cs_br_type === BR_GE || cs_br_type === BR_GEU ||
                  cs_br_type === BR_LT || cs_br_type === BR_LTU

   val redirect_type = Mux((cs_br_type === BR_J || cs_br_type === BR_JR) & dec_wbaddr(0),RD_CALL, 
                        Mux(cs_br_type === BR_JR && (dec_rs1_addr === 1.U), RD_RET,
                        Mux(cs_br_type === BR_J || cs_br_type === BR_JR, RD_JAL, 
                        Mux(is_cond_br,RD_BR,RD_X))))


   val inst_ctrl_block = InstCtrlBlock.apply(valid = cs_val_inst,
                                             inst        = dec_reg_inst,
                                             pc          = dec_reg_pc,
                                             rs1_addr    = dec_rs1_addr,
                                             rs2_addr    = dec_rs2_addr,
                                             wbaddr      = dec_wbaddr,
                                             imm         = imm,
                                             exception   = io.exception)  
   val with_mem = InstCtrlBlock.memoryOp(  mem_val = cs_mem_en,
                              mem_fcn = cs_mem_fcn,
                              mem_typ = cs_msk_sel,

                              base = inst_ctrl_block) 
   val with_br =  InstCtrlBlock.branchOp(  base = with_mem,
                              br_type = cs_br_type,
                              redirect_type = redirect_type,
                              bpu_resp    = io.bpu_resp,
                              fencei = cs_fencei)
   val with_csr = InstCtrlBlock.csrOp(    base = with_br,
                                          csr_cmd = cs_csr_cmd)
   val with_alu = InstCtrlBlock.aluOp(     base = with_csr,
                              alu_fun = cs_alu_fun,
                              op1_sel = cs_op1_sel,  
                              op2_sel = cs_op2_sel)
   val result =   InstCtrlBlock.wbOp(     base = with_alu, 
                              wb_sel = cs_wb_sel,
                              rf_wen = cs_rf_wen)

   io.dec_out := result

   /////////   Debug Signals
   val perfCounters = RegInit(VecInit(Seq.fill(9)(0.U(conf.perfCountBits.W))))
   val Seq( loadCount, storeCount, jtypeCount, btypeCount, utypeCount, itypeCount, 
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
   when(io.valid){
      when(isLoad) {
         loadCount := loadCount + 1.U
      }.elsewhen(isStore) {
         storeCount := storeCount + 1.U
      }.elsewhen(isBranch) {
         btypeCount := btypeCount + 1.U
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
   io.debug.btypeCount  := btypeCount  
   io.debug.utypeCount  := utypeCount
   io.debug.otherCount  := otherCount
}
