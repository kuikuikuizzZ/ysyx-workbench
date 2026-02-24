package npc.galois

import chisel3._
import chisel3.util._

import npc._
import npc.common._
import npc.galois.Constants._
import npc.common.UtilMethods.{ResultHoldBypass}

trait HasOOOParams{
    implicit val conf: Config
    val ROB_SIZE = 64
    val IQ_SIZE = 16
    val LQ_SIZE = 16
    val SQ_SIZE = 16
    val PRF_SIZE = 128
    val ARC_SIZE = 32
    val PRF_BITS = log2Ceil(PRF_SIZE)
    val ARC_BITS = log2Ceil(ARC_SIZE)
    val ROB_BITS = log2Ceil(ROB_SIZE)
    val IQ_BITS = log2Ceil(IQ_SIZE)
 
}

abstract class OOOModule extends Module with HasOOOParams
abstract class OOOBundle extends Bundle with HasOOOParams

class MemCtrlIO (implicit val conf: Config) extends OOOBundle {
    val mem_val    = (Bool())
    val mem_fcn    = (UInt(M_X.getWidth.W))
    val mem_typ    = (UInt(MT_X.getWidth.W))
}

class BranchJumpIO (implicit val conf: Config) extends OOOBundle {
    val br_type         = (UInt(BR_N.getWidth.W))
    val redirect_type   = (UInt(RD_X.getWidth.W))
    val fencei          = (Bool())
    val bpu_resp        = (new BPUResp)
}

class BJUOut(implicit val conf: Config) extends Bundle() {
   val should_redirect     =   (Bool())
   val taken               =   (Bool()) 
   val predict_wrong       =   (Bool())
   val redirect_type       =   (UInt(RD_X.getWidth.W))
}

class ALUCtrlIO (implicit val conf: Config) extends OOOBundle {
    val op1_sel    = (UInt(OP1_X.getWidth.W))
    val op2_sel    = (UInt(OP2_X.getWidth.W))
    val alu_fun    = (UInt(ALU_X.getWidth.W))
    val rs1_oen     = (Bool())
    val rs2_oen     = (Bool())
}

class WBCtrlIO (implicit val conf: Config) extends OOOBundle {
    val wb_sel      = (UInt(WB_X.getWidth.W))
    val rf_wen      = (Bool())
}

class CSRCtrlIO (implicit val conf: Config) extends OOOBundle {
    val csr_cmd           = (UInt(CSR.N.getWidth.W))
    val eret              = (Bool())
    val ebreak            = (Bool())
}

class InstCtrlBlock (implicit val conf: Config) extends OOOBundle {
    val valid             = (Bool())
    val pc                = (UInt(conf.xprlen.W))
    val pc_sel            = (UInt(PC_4.getWidth.W))
    val target            = (UInt(conf.xprlen.W))
    val exception         = (UInt(EXC_NORMAL.getWidth.W))
    val inst              = (UInt(conf.xlen.W))
    //reorder 
    val finish            = Bool()
    val reorder_num       = UInt(ROB_BITS.W)
    //regs  
    val wbaddr            = UInt(ARC_BITS.W)
    val rs1_addr          = UInt(ARC_BITS.W)
    val rs2_addr          = UInt(ARC_BITS.W)
    //preg
    val prs1_addr         = UInt(PRF_BITS.W)
    val prs2_addr         = UInt(PRF_BITS.W)
    val prs_wbaddr        = UInt(PRF_BITS.W)
    val cmt_wbaddr        = UInt(PRF_BITS.W)
    //exe
    val rs1_data          = (UInt(conf.xprlen.W))
    val rs2_data          = (UInt(conf.xprlen.W))
    val wb_data           = (UInt(conf.xprlen.W))
    val imm               = (UInt(conf.xprlen.W))
    val alu_out           = (UInt(conf.xlen.W))
    // ctrl signals
    val wb_ctrl            = new WBCtrlIO()
    val alu_ctrl          = new ALUCtrlIO()
    val mem_ctrl          = new MemCtrlIO()
    val br_ctrl           = new BranchJumpIO()
    val csr_ctrl          = new CSRCtrlIO()
    val bpu_resp          = new BPUResp()
    val bju_out           = new BJUOut()
}

class BlockLineIO (implicit val conf: Config) extends OOOBundle {
    val instA = Input(new InstCtrlBlock)
    val instB = Input(new InstCtrlBlock)
}


object InstCtrlBlock {
  // 生成全默认值（Bubble指令）的InstCtrlBlock
  // def apply()(implicit conf: Config): InstCtrlBlock = {
  //   val bubble = WireInit(0.U.asTypeOf(new InstCtrlBlock()))
    
  //   // 基本控制信号
  //   bubble.valid := false.B
  //   bubble.inst := BUBBLE
  //   bubble.pc := 0.U
  //   bubble.pc_sel := PC_X
  //   bubble.target := 0.U
  //   bubble.exception := EXC_NORMAL
  //   bubble.finish := false.B
  //   bubble.reorder_num := 0.U
    
  //   // 寄存器相关字段
  //   bubble.wbaddr := 0.U
  //   bubble.rs1_addr := 0.U
  //   bubble.rs2_addr := 0.U
  //   bubble.prs1_addr := 0.U
  //   bubble.prs2_addr := 0.U
  //   bubble.prs_wbaddr := 0.U
  //   bubble.cmt_wbaddr := 0.U
  //   // 数据字段
  //   bubble.rs1_data := 0.U
  //   bubble.rs2_data := 0.U
  //   bubble.imm      := 0.U
  //   bubble.wb_data  := 0.U
  //   bubble.alu_out := 0.U
  //   // 初始化嵌套的Bundle - 写回控制
  //   bubble.wb_ctrl.wb_sel := WB_X
  //   bubble.wb_ctrl.rf_wen := false.B
    
  //   // 初始化嵌套的Bundle - ALU控制
  //   bubble.alu_ctrl.op1_sel := OP1_X
  //   bubble.alu_ctrl.op2_sel := OP2_X
  //   bubble.alu_ctrl.alu_fun := ALU_X
    
  //   // 初始化嵌套的Bundle - 内存控制
  //   bubble.mem_ctrl.mem_val := false.B
  //   bubble.mem_ctrl.mem_fcn := M_X
  //   bubble.mem_ctrl.mem_typ := MT_X
    
  //   // 初始化嵌套的Bundle - 分支跳转控制
  //   bubble.br_ctrl.br_type := BR_N
  //   bubble.br_ctrl.redirect_type := RD_X
  //   bubble.br_ctrl.bpu_resp := 0.U.asTypeOf(new BPUResp())
    
  //   // 初始化嵌套的Bundle - CSR控制
  //   bubble.csr_ctrl.csr_cmd := CSR.N
  //   bubble.csr_ctrl.exception := false.B
    
  //   bubble
  // }

  // 完整参数化的apply函数
  def apply(
    // 基本参数
    valid: Bool,
    pc: UInt,
    inst: UInt,
    target: UInt = 0.U,
    exception: UInt = EXC_NORMAL,
    pc_sel: UInt = 0.U,
    
    // 重排序相关
    finish: Bool = false.B,
    reorder_num: UInt = 0.U,
    
    // 架构寄存器
    wbaddr: UInt = 0.U,
    rs1_addr: UInt = 0.U,
    rs2_addr: UInt = 0.U,
    
    // 物理寄存器
    prs1_addr: UInt = 0.U,
    prs2_addr: UInt = 0.U,
    prs_wbaddr: UInt = 0.U,
    cmt_wbaddr: UInt = 0.U,
    
    // 数据
    rs1_data: UInt = 0.U,
    rs2_data: UInt = 0.U,
    imm     : UInt = 0.U,
    wb_data : UInt = 0.U,
    alu_out : UInt = 0.U,
    
    // 控制信号Bundle
    wb_ctrl: WBCtrlIO = null,
    alu_ctrl: ALUCtrlIO = null,
    mem_ctrl: MemCtrlIO = null,
    br_ctrl: BranchJumpIO = null,
    csr_ctrl: CSRCtrlIO = null,
    bju_out: BJUOut = null,
  )(implicit conf: Config): InstCtrlBlock = {
    
    val icb = WireDefault(0.U.asTypeOf(new InstCtrlBlock()))
    
    icb.valid := valid
    icb.pc          := pc
    icb.pc_sel      := pc_sel
    icb.target      := target
    icb.exception   := exception
    icb.inst        := inst
    icb.finish      := finish
    icb.reorder_num := reorder_num
    icb.wbaddr      := wbaddr
    icb.rs1_addr    := rs1_addr
    icb.rs2_addr    := rs2_addr
    icb.prs1_addr   := prs1_addr
    icb.prs2_addr   := prs2_addr
    icb.prs_wbaddr  := prs_wbaddr
    icb.cmt_wbaddr  := cmt_wbaddr
    icb.rs1_data    := rs1_data
    icb.rs2_data    := rs2_data
    icb.imm         := imm
    icb.wb_data     := wb_data
    icb.alu_out     := alu_out
    if (wb_ctrl != null) {
      icb.wb_ctrl.wb_sel := wb_ctrl.wb_sel
      icb.wb_ctrl.rf_wen := wb_ctrl.rf_wen
    }
    
    if (alu_ctrl != null) {
      icb.alu_ctrl.op1_sel := alu_ctrl.op1_sel
      icb.alu_ctrl.op2_sel := alu_ctrl.op2_sel
      icb.alu_ctrl.alu_fun := alu_ctrl.alu_fun
    }
    
    if (mem_ctrl != null) {
      icb.mem_ctrl.mem_val := mem_ctrl.mem_val
      icb.mem_ctrl.mem_fcn := mem_ctrl.mem_fcn
      icb.mem_ctrl.mem_typ := mem_ctrl.mem_typ
    }
    
    if (br_ctrl != null) {
      icb.br_ctrl.br_type := br_ctrl.br_type
      icb.br_ctrl.redirect_type := br_ctrl.redirect_type
      icb.br_ctrl.bpu_resp := br_ctrl.bpu_resp
    }
    
    if (csr_ctrl != null) {
      icb.csr_ctrl.csr_cmd := csr_ctrl.csr_cmd
    }
    if (bju_out != null) {
      icb.bju_out.should_redirect := bju_out.should_redirect
      icb.bju_out.taken           := bju_out.taken
      icb.bju_out.predict_wrong   := bju_out.predict_wrong
      icb.bju_out.redirect_type   := bju_out.redirect_type
    }
    icb
  }

  def copy(
    base: InstCtrlBlock, 
    valid: Option[Bool] = None,
    pc: Option[UInt] = None,
    pc_sel: Option[UInt] = None,
    target: Option[UInt] = None,
    exception: Option[UInt] = None,
    inst: Option[UInt] = None,
    finish: Option[Bool] = None,
    reorder_num: Option[UInt] = None,
    wbaddr: Option[UInt] = None,
    rs1_addr: Option[UInt] = None,
    rs2_addr: Option[UInt] = None,
    prs1_addr: Option[UInt] = None,
    prs2_addr: Option[UInt] = None,
    prs_wbaddr: Option[UInt] = None,
    cmt_wbaddr: Option[UInt] = None,
    rs1_data: Option[UInt] = None,
    rs2_data: Option[UInt] = None,
    wb_data: Option[UInt] = None,
    alu_out: Option[UInt] = None,
    imm: Option[UInt] = None,
    // 对于嵌套的 Bundle，同样使用 Option
    wb_ctrl: Option[WBCtrlIO] = None,
    alu_ctrl: Option[ALUCtrlIO] = None,
    mem_ctrl: Option[MemCtrlIO] = None,
    br_ctrl: Option[BranchJumpIO] = None,
    csr_ctrl: Option[CSRCtrlIO] = None,
    bpu_resp: Option[BPUResp] = None,
    bju_out: Option[BJUOut] = None
  )(implicit conf: Config): InstCtrlBlock = {
    
    // 1. 克隆基础实例
    val newIcb = WireInit(base)

    // 2. 条件化覆盖字段：仅当参数为 Some(value) 时才更新
    valid.foreach { v => newIcb.valid := v }
    pc.foreach { p => newIcb.pc := p }
    pc_sel.foreach { ps => newIcb.pc_sel := ps }
    target.foreach { t => newIcb.target := t }
    exception.foreach { e => newIcb.exception := e }
    inst.foreach { i => newIcb.inst := i }
    finish.foreach { f => newIcb.finish := f }
    reorder_num.foreach { rn => newIcb.reorder_num := rn }
    wbaddr.foreach { wa => newIcb.wbaddr := wa }
    rs1_addr.foreach { r1a => newIcb.rs1_addr := r1a }
    rs2_addr.foreach { r2a => newIcb.rs2_addr := r2a }
    prs1_addr.foreach { p1a => newIcb.prs1_addr := p1a }
    prs2_addr.foreach { p2a => newIcb.prs2_addr := p2a }
    prs_wbaddr.foreach { pwa => newIcb.prs_wbaddr := pwa }
    cmt_wbaddr.foreach { cwa => newIcb.cmt_wbaddr := cwa }
    rs1_data.foreach { r1d => newIcb.rs1_data := r1d }
    rs2_data.foreach { r2d => newIcb.rs2_data := r2d }
    wb_data.foreach { wd => newIcb.wb_data := wd }
    alu_out.foreach { ao => newIcb.alu_out := ao }
    imm.foreach { i => newIcb.imm := i }
    // 3. 处理嵌套的 Bundle 字段
    wb_ctrl.foreach { wc =>
      newIcb.wb_ctrl.wb_sel := wc.wb_sel
      newIcb.wb_ctrl.rf_wen := wc.rf_wen
    }
    alu_ctrl.foreach { ac =>
      newIcb.alu_ctrl.op1_sel := ac.op1_sel
      newIcb.alu_ctrl.op2_sel := ac.op2_sel
      newIcb.alu_ctrl.alu_fun := ac.alu_fun
      newIcb.alu_ctrl.rs1_oen := ac.rs1_oen
      newIcb.alu_ctrl.rs2_oen := ac.rs2_oen
    }
    mem_ctrl.foreach { mc =>
      newIcb.mem_ctrl.mem_val := mc.mem_val
      newIcb.mem_ctrl.mem_fcn := mc.mem_fcn
      newIcb.mem_ctrl.mem_typ := mc.mem_typ
    }
    br_ctrl.foreach { bc =>
      newIcb.br_ctrl.br_type := bc.br_type
      newIcb.br_ctrl.redirect_type := bc.redirect_type
      newIcb.br_ctrl.bpu_resp := bc.bpu_resp
    }
    csr_ctrl.foreach { cc =>
      newIcb.csr_ctrl.csr_cmd := cc.csr_cmd
      newIcb.csr_ctrl.eret    := cc.eret
      newIcb.csr_ctrl.ebreak  := cc.ebreak
    }
    bpu_resp.foreach { br => newIcb.bpu_resp := br }
    bju_out.foreach { bo => 
      newIcb.bju_out.should_redirect  := bo.should_redirect
      newIcb.bju_out.taken            := bo.taken         
      newIcb.bju_out.predict_wrong    := bo.predict_wrong  
      newIcb.bju_out.redirect_type    := bo.redirect_type  
    }
    // 4. 返回新实例
    newIcb
  }



  // 专门用于内存操作的工厂方法
  def memoryOp(
    mem_val: Bool,
    mem_fcn: UInt,
    mem_typ: UInt,
    base: InstCtrlBlock
  )(implicit conf: Config): InstCtrlBlock = {
    
    val mem_ctrl = WireDefault(0.U.asTypeOf(new MemCtrlIO())) 
    mem_ctrl.mem_val := mem_val
    mem_ctrl.mem_fcn := mem_fcn
    mem_ctrl.mem_typ := mem_typ
    copy(
      base = base,
      mem_ctrl = Some(mem_ctrl)
    )
  }

  // 专门用于分支跳转的工厂方法
  def branchOp(
    br_type: UInt,
    redirect_type: UInt,
    fencei: Bool,
    bpu_resp: BPUResp,
    base: InstCtrlBlock
  )(implicit conf: Config): InstCtrlBlock = {
    
    val br_ctrl = WireDefault(0.U.asTypeOf(new BranchJumpIO())) 
    br_ctrl.br_type := br_type
    br_ctrl.redirect_type := redirect_type
    br_ctrl.fencei := fencei
    br_ctrl.bpu_resp := bpu_resp

    copy(
      base = (base),
      br_ctrl = Some(br_ctrl)
    )
  }  
  def aluOp(
    op1_sel: UInt,
    op2_sel: UInt,
    rs1_oen: Bool,
    rs2_oen: Bool,
    alu_fun: UInt,
    base: InstCtrlBlock
  )(implicit conf: Config): InstCtrlBlock = {
    
    val alu_ctrl = WireDefault(0.U.asTypeOf(new ALUCtrlIO()))
    alu_ctrl.op1_sel := op1_sel
    alu_ctrl.op2_sel := op2_sel
    alu_ctrl.alu_fun := alu_fun
    alu_ctrl.rs1_oen := rs1_oen
    alu_ctrl.rs2_oen := rs2_oen
    
    copy(
      base = (base),
      alu_ctrl = Some(alu_ctrl)
    )
  }

  def csrOp(
    base: InstCtrlBlock,
    csr_cmd: UInt = CSR.N,
    pc_sel: UInt = 0.U,
    exception: Bool = false.B,
    target: UInt = 0.U,
    wb_data: UInt = 0.U,
    eret: Bool = false.B,
    ebreak: Bool = false.B
  )(implicit conf: Config): InstCtrlBlock = {
    
    val csr_ctrl = WireDefault(0.U.asTypeOf(new CSRCtrlIO()))
  
    csr_ctrl.csr_cmd := csr_cmd
    csr_ctrl.eret := eret
    csr_ctrl.ebreak := ebreak

    copy(
      base = base,
      pc_sel = Some(pc_sel),
      wb_data = Some(wb_data),
      csr_ctrl = Some(csr_ctrl),
      target = Some(target),
      exception = Some(exception)
    )
  }

  def wbOp(
    wb_sel: UInt,
    rf_wen: Bool,
    base: InstCtrlBlock
  )(implicit conf: Config): InstCtrlBlock = {
    
    val wb_ctrl = WireDefault(0.U.asTypeOf(new WBCtrlIO()))
    wb_ctrl.wb_sel := wb_sel
    wb_ctrl.rf_wen := rf_wen
    
    copy(
      base = (base),
      wb_ctrl = Some(wb_ctrl)
    )
  }

  def bjuOp(
    pc                 : UInt,
    target             : UInt,
    pc_sel             : UInt,
    should_redirect    : Bool,
    taken              : Bool,
    wb_data            : UInt,
    predict_wrong      : Bool,
    redirect_type      : UInt,
    base: InstCtrlBlock
  )(implicit conf: Config): InstCtrlBlock = {

    val bju_out =  WireDefault(0.U.asTypeOf(new BJUOut())) 
    bju_out.should_redirect    := should_redirect
    bju_out.taken              := taken
    bju_out.predict_wrong      := predict_wrong
    bju_out.redirect_type      := redirect_type
    copy(
      base = (base),
      pc = Some(pc),
      bju_out = Some(bju_out),
      wb_data = Some(wb_data),
      pc_sel = Some(pc_sel),
      target = Some(target)
    )
  }
}