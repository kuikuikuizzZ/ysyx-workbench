package npc

import chisel3._
import chisel3.util._

import npc.common.Instructions._
import npc.common._
import npc.Constants._

class CtlToDatIo extends Bundle()
{
   val op1_sel   = Output(UInt(OP1_X.getWidth.W))
   val op2_sel   = Output(UInt(OP2_X.getWidth.W))
   val alu_fun   = Output(UInt(ALU_X.getWidth.W))
   val csr_cmd   = Output(UInt(CSR.SZ.W))
   val br_type   = Output(UInt(BR_N.getWidth.W))
   val exception = Output(Bool())
}


class CtlToLSUIo extends Bundle()
{  
   val mem_en     = Output(Bool())
   val mem_fcn    = Output(UInt(M_X.getWidth.W))
   val msk_sel    = Output(UInt(MT_X.getWidth.W))
}

class CtlToWBIo(implicit val conf: ysyx_24100012_Config) extends Bundle()
{
   val rf_wen = Output(Bool())
   val wb_sel = Output(UInt(WB_X.getWidth.W))
   val exception = Output(Bool())
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
   val inst = Input(UInt(conf.xlen.W))
   val ctl  = new CtlToDatIo()
   val ctl_lsu = new CtlToLSUIo()
   val ctl_wb    = new CtlToWBIo()
   val pipeline_kill = Output(Bool())
   val ifu_valid   = Input(Bool())
   val ls_valid   =  Input(Bool())
   val pc_io      =  Flipped(new PCOut())
   val finish     = Output(Bool())
}

class ysyx_24100012_Decoder(implicit val conf: ysyx_24100012_Config) extends Module
{
   val io = IO(new CpathIo())
   io := DontCare
   
   // Control Signals
   val csignals =
      ListLookup(io.inst,                                                                                       
                             List(N, BR_N  , OP1_X  ,  OP2_X  , ALU_X   , WB_X   , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
               Array(       /* val  |  BR  |  op1   |   op2     |  ALU    |  wb  | rf   | mem  | mem  | mask |  csr  */
                            /* inst | type |   sel  |    sel    |   fcn   |  sel | wen  |  en  |  wr  | type |  cmd  */
                  LW      -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_W,  CSR.N),
                  LB      -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_B,  CSR.N),
                  LBU     -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_BU, CSR.N),
                  LH      -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_H,  CSR.N),
                  LHU     -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_HU, CSR.N),
                  SW      -> List(Y, BR_N  , OP1_RS1, OP2_IMS , ALU_ADD ,  WB_X  , REN_0, MEN_1, M_XWR, MT_W,  CSR.N),
                  SB      -> List(Y, BR_N  , OP1_RS1, OP2_IMS , ALU_ADD ,  WB_X  , REN_0, MEN_1, M_XWR, MT_B,  CSR.N),
                  SH      -> List(Y, BR_N  , OP1_RS1, OP2_IMS , ALU_ADD ,  WB_X  , REN_0, MEN_1, M_XWR, MT_H,  CSR.N),
                  
                  AUIPC   -> List(Y, BR_N  , OP1_IMU, OP2_PC  , ALU_ADD ,  WB_ALU, REN_1, MEN_0, M_X ,  MT_X,  CSR.N),
                  LUI     -> List(Y, BR_N  , OP1_IMU, OP2_X   , ALU_COPY1, WB_ALU, REN_1, MEN_0, M_X ,  MT_X,  CSR.N),
                 
                  ADDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  ANDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_AND ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  ORI     -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_OR  ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  XORI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_XOR ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLTI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SLT ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLTIU   -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SLTU,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SLL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SRAI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SRA ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SRLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SRL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                   
                  SLL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SLL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  ADD     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_ADD ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SUB     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SUB ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLT     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SLT ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLTU    -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SLTU,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  AND     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_AND ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  OR      -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_OR  ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  XOR     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_XOR ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SRA     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SRA ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SRL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SRL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  
                  JAL     -> List(Y, BR_J  , OP1_X  , OP2_X   , ALU_X   ,  WB_PC4, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  JALR    -> List(Y, BR_JR , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_PC4, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  BEQ     -> List(Y, BR_EQ , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BNE     -> List(Y, BR_NE , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BGE     -> List(Y, BR_GE , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BGEU    -> List(Y, BR_GEU, OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BLT     -> List(Y, BR_LT , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BLTU    -> List(Y, BR_LTU, OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
  
                  CSRRWI  -> List(Y, BR_N  , OP1_IMZ, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.W),
                  CSRRSI  -> List(Y, BR_N  , OP1_IMZ, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.S),
                  CSRRCI  -> List(Y, BR_N  , OP1_IMZ, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.C),
                  CSRRW   -> List(Y, BR_N  , OP1_RS1, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.W),
                  CSRRS   -> List(Y, BR_N  , OP1_RS1, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.S),
                  CSRRC   -> List(Y, BR_N  , OP1_RS1, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.C),
           
                  ECALL   -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.I),
                  MRET    -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.I),
                  DRET    -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.I),
                  EBREAK  -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.I),
                  WFI     -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N), // implemented as a NOP

                  FENCE_I -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  FENCE   -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_1, M_X  , MT_X,  CSR.N)
                  // we are already sequentially consistent, so no need to honor the fence instruction
                  ))

   // Put these control signals into variables
   val (cs_val_inst: Bool) :: (cs_br_type:UInt)         :: cs_op1_sel     :: cs_op2_sel :: cs0 = csignals
   val cs_alu_fun          :: cs_wb_sel          :: (cs_rf_wen: Bool)     ::               cs1 = cs0
   val (cs_mem_en: Bool)   :: cs_mem_fcn         :: cs_msk_sel            :: (cs_csr_cmd:UInt) :: Nil = cs1

   // val rs1_addr = io.inst(RS1_MSB, RS1_LSB)
   // val rs2_addr = io.inst(RS2_MSB, RS2_LSB)
   // val wb_addr  = io.inst(RD_MSB, RD_LSB)

   // cs_op1_sel     cs_op1_sel
   // cs_op2_sel     cs_op2_sel
   // cs_alu_fun     cs_alu_fun
   // cs_br_type     cs_br_type
   // cs_mem_en      cs_mem_en
   // cs_mem_fcn     cs_mem_fcn
   // cs_msk_sel     cs_msk_sel
   // cs_rf_wen      cs_rf_wen
   // cs_wb_sel      cs_wb_sel

   // rs1_addr       rs1_addr 
   // rs2_addr       rs2_addr 
   // wb_addr        wb_addr  
   // Set the data-path control signals
   io.ctl.op1_sel       :=      cs_op1_sel
   io.ctl.op2_sel       :=      cs_op2_sel
   io.ctl.alu_fun       :=      cs_alu_fun
   io.ctl.br_type       :=      cs_br_type

   val mem_en            =       Mux(io.ifu_valid, cs_mem_en, MEN_0)  
   io.ctl_lsu.mem_en    :=       mem_en
   io.ctl_lsu.mem_fcn   :=       cs_mem_fcn
   io.ctl_lsu.msk_sel   :=       cs_msk_sel
   io.ctl_wb.exception  :=       io.ctl.exception
   
   io.finish := Mux(cs_mem_en, io.ls_valid, io.ifu_valid) 

   io.ctl_wb.rf_wen     := Mux(cs_mem_en, 
                              Mux( io.ls_valid  , cs_rf_wen, REN_0),
                              Mux( io.ifu_valid , cs_rf_wen, REN_0))  
   io.ctl_wb.wb_sel     :=       cs_wb_sel
   
   // convert CSR instructions with raddr1 == 0 to read-only CSR commands
   val rs1_addr = io.inst(RS1_MSB, RS1_LSB)
   val csr_ren = (cs_csr_cmd === CSR.S || cs_csr_cmd === CSR.C) && rs1_addr === 0.U
   val csr_cmd = Mux(csr_ren, CSR.R, cs_csr_cmd)

   // io.ctl.csr_cmd  := Mux(stall, CSR.N, csr_cmd)
   io.ctl.csr_cmd := csr_cmd


   
   // Exception Handling ---------------------
   // We only need to check if the instruction is illegal (or unsupported)
   // or if the CSR file wants us to be interrupted.
   // Other exceptions are detected later in the pipeline by passing the
   // instruction to the CSR File and letting it redirect the PC as it sees
   // fit.
   io.ctl.exception := (!cs_val_inst && io.ifu_valid) 
   io.pipeline_kill :=  (!cs_val_inst ) 

   /////////   Debug Signals
   val perfCounters = RegInit(VecInit(Seq.fill(8)(0.U(conf.perfCountBits.W))))
   val Seq( loadCount, storeCount, jtypeCount, utypeCount, itypeCount, 
            rtypeCount, csrCount, otherCount ) = perfCounters
  // 加载指令检测
   val isLoad = io.inst === LB || io.inst === LH || io.inst === LW || 
                  io.inst === LBU || io.inst === LHU
   
   // 存储指令检测
   val isStore = io.inst === SB || io.inst === SH || io.inst === SW
   
   // 分支指令检测
   val isBranch = io.inst === BEQ || io.inst === BNE || 
                  io.inst === BLT || io.inst === BGE || 
                  io.inst === BLTU || io.inst === BGEU
   
   // 跳转指令检测
   val isJump = io.inst === JAL || io.inst === JALR
   
   // I型指令检测
   val isIType = io.inst === ADDI || io.inst === ANDI || io.inst === ORI || 
                  io.inst === XORI || io.inst === SLTI || io.inst === SLTIU || 
                  io.inst === SLLI || io.inst === SRAI || io.inst === SRLI
   
   // R型指令检测
   val isRType = io.inst === ADD || io.inst === SUB || io.inst === SLL || 
                  io.inst === SLT || io.inst === SLTU || io.inst === XOR || 
                  io.inst === SRL || io.inst === SRA || io.inst === OR || 
                  io.inst === AND
   
   // CSR指令检测
   val isCSR = io.inst === CSRRSI ||io.inst === CSRRCI ||io.inst === CSRRW || io.inst === CSRRS || 
         io.inst === CSRRC || io.inst === ECALL || io.inst === MRET ||   io.inst === DRET || 
         io.inst === EBREAK ||io.inst === WFI  || io.inst === FENCE_I || io.inst === FENCE  

   val isUtype = io.inst === LUI || io.inst === AUIPC
   when(io.ifu_valid){
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
