package npc.common

import chisel3._
import chisel3.util._

class MStatus extends Bundle {
    // not truly part of mstatus, but convenient
  val debug = Bool()
  val prv = UInt(PRV.SZ.W) // not truly part of mstatus, but convenient
  val sd = Bool()
  val zero1 = UInt(8.W)
  val tsr = Bool()
  val tw = Bool()
  val tvm = Bool()
  val mxr = Bool()
  val sum = Bool()
  val mprv = Bool()
  val xs = UInt(2.W)
  val fs = UInt(2.W)
  val mpp = UInt(2.W)
  val hpp = UInt(2.W)
  val spp = UInt(1.W)
  val mpie = Bool()
  val hpie = Bool()
  val spie = Bool()
  val upie = Bool()
  val mie = Bool()
  val hie = Bool()
  val sie = Bool()
  val uie = Bool()
}

object PRV
{
  val SZ = 2
  val U = 0.U(SZ.W)
  val S = 1.U(SZ.W)
  val H = 2.U(SZ.W)
  val M = 3.U(SZ.W)
}

object CSR
{
  // commands
  val SZ = 3
  def X = BitPat.dontCare(SZ)
  def N = 0.U(SZ.W)
  def R = 2.U(SZ.W)
  def I = 4.U(SZ.W)
  def W = 5.U(SZ.W)
  def S = 6.U(SZ.W)
  def C = 7.U(SZ.W)

  val ADDRSZ = 12
  val firstCtr = CSRs.cycle
  val firstCtrH = CSRs.cycleh
  val firstHPC = CSRs.hpmcounter3
  val firstHPCH = CSRs.hpmcounter3h
  //val firstHPE = CSRs.mhpmevent3
  val firstMHPC = CSRs.mhpmcounter3
  val firstMHPCH = CSRs.mhpmcounter3h
  val firstHPM = 3
  val nCtr = 32
  val nHPM = nCtr - firstHPM
  val hpmWidth = 40
}

object CSRs {
  val cycle = 0xc00
  val instret = 0xc02
  val hpmcounter3 = 0xc03
  val hpmcounter4 = 0xc04
  val hpmcounter5 = 0xc05
  val hpmcounter6 = 0xc06
  val hpmcounter7 = 0xc07
  val hpmcounter8 = 0xc08
  val hpmcounter9 = 0xc09
  val hpmcounter10 = 0xc0a
  val hpmcounter11 = 0xc0b
  val hpmcounter12 = 0xc0c
  val hpmcounter13 = 0xc0d
  val hpmcounter14 = 0xc0e
  val hpmcounter15 = 0xc0f
  val hpmcounter16 = 0xc10
  val hpmcounter17 = 0xc11
  val hpmcounter18 = 0xc12
  val hpmcounter19 = 0xc13
  val hpmcounter20 = 0xc14
  val hpmcounter21 = 0xc15
  val hpmcounter22 = 0xc16
  val hpmcounter23 = 0xc17
  val hpmcounter24 = 0xc18
  val hpmcounter25 = 0xc19
  val hpmcounter26 = 0xc1a
  val hpmcounter27 = 0xc1b
  val hpmcounter28 = 0xc1c
  val hpmcounter29 = 0xc1d
  val hpmcounter30 = 0xc1e
  val hpmcounter31 = 0xc1f
  val mstatus = 0x300
  val misa = 0x301
  val medeleg = 0x302
  val mideleg = 0x303
  val mie = 0x304
  val mtvec = 0x305
  val mscratch = 0x340
  val mcounteren = 0x306
  val mepc = 0x341
  val mcause = 0x342
  val mtval = 0x343
  val mip = 0x344
  val tselect = 0x7a0
  val tdata1 = 0x7a1
  val tdata2 = 0x7a2
  val tdata3 = 0x7a3
  val dcsr = 0x7b0
  val dpc = 0x7b1
  val dscratch = 0x7b2
  val mcycle = 0xb00
  val minstret = 0xb02
  val mhpmcounter3 = 0xb03
  val mhpmcounter4 = 0xb04
  val mhpmcounter5 = 0xb05
  val mhpmcounter6 = 0xb06
  val mhpmcounter7 = 0xb07
  val mhpmcounter8 = 0xb08
  val mhpmcounter9 = 0xb09
  val mhpmcounter10 = 0xb0a
  val mhpmcounter11 = 0xb0b
  val mhpmcounter12 = 0xb0c
  val mhpmcounter13 = 0xb0d
  val mhpmcounter14 = 0xb0e
  val mhpmcounter15 = 0xb0f
  val mhpmcounter16 = 0xb10
  val mhpmcounter17 = 0xb11
  val mhpmcounter18 = 0xb12
  val mhpmcounter19 = 0xb13
  val mhpmcounter20 = 0xb14
  val mhpmcounter21 = 0xb15
  val mhpmcounter22 = 0xb16
  val mhpmcounter23 = 0xb17
  val mhpmcounter24 = 0xb18
  val mhpmcounter25 = 0xb19
  val mhpmcounter26 = 0xb1a
  val mhpmcounter27 = 0xb1b
  val mhpmcounter28 = 0xb1c
  val mhpmcounter29 = 0xb1d
  val mhpmcounter30 = 0xb1e
  val mhpmcounter31 = 0xb1f
  val mucounteren = 0x320
  val mhpmevent3 = 0x323
  val mhpmevent4 = 0x324
  val mhpmevent5 = 0x325
  val mhpmevent6 = 0x326
  val mhpmevent7 = 0x327
  val mhpmevent8 = 0x328
  val mhpmevent9 = 0x329
  val mhpmevent10 = 0x32a
  val mhpmevent11 = 0x32b
  val mhpmevent12 = 0x32c
  val mhpmevent13 = 0x32d
  val mhpmevent14 = 0x32e
  val mhpmevent15 = 0x32f
  val mhpmevent16 = 0x330
  val mhpmevent17 = 0x331
  val mhpmevent18 = 0x332
  val mhpmevent19 = 0x333
  val mhpmevent20 = 0x334
  val mhpmevent21 = 0x335
  val mhpmevent22 = 0x336
  val mhpmevent23 = 0x337
  val mhpmevent24 = 0x338
  val mhpmevent25 = 0x339
  val mhpmevent26 = 0x33a
  val mhpmevent27 = 0x33b
  val mhpmevent28 = 0x33c
  val mhpmevent29 = 0x33d
  val mhpmevent30 = 0x33e
  val mhpmevent31 = 0x33f
  val mvendorid = 0xf11
  val marchid = 0xf12
  val mimpid = 0xf13
  val mhartid = 0xf14
  val cycleh = 0xc80
  val instreth = 0xc82
  val hpmcounter3h = 0xc83
  val hpmcounter4h = 0xc84
  val hpmcounter5h = 0xc85
  val hpmcounter6h = 0xc86
  val hpmcounter7h = 0xc87
  val hpmcounter8h = 0xc88
  val hpmcounter9h = 0xc89
  val hpmcounter10h = 0xc8a
  val hpmcounter11h = 0xc8b
  val hpmcounter12h = 0xc8c
  val hpmcounter13h = 0xc8d
  val hpmcounter14h = 0xc8e
  val hpmcounter15h = 0xc8f
  val hpmcounter16h = 0xc90
  val hpmcounter17h = 0xc91
  val hpmcounter18h = 0xc92
  val hpmcounter19h = 0xc93
  val hpmcounter20h = 0xc94
  val hpmcounter21h = 0xc95
  val hpmcounter22h = 0xc96
  val hpmcounter23h = 0xc97
  val hpmcounter24h = 0xc98
  val hpmcounter25h = 0xc99
  val hpmcounter26h = 0xc9a
  val hpmcounter27h = 0xc9b
  val hpmcounter28h = 0xc9c
  val hpmcounter29h = 0xc9d
  val hpmcounter30h = 0xc9e
  val hpmcounter31h = 0xc9f
  val mcycleh = 0xb80
  val minstreth = 0xb82
  val mhpmcounter3h = 0xb83
  val mhpmcounter4h = 0xb84
  val mhpmcounter5h = 0xb85
  val mhpmcounter6h = 0xb86
  val mhpmcounter7h = 0xb87
  val mhpmcounter8h = 0xb88
  val mhpmcounter9h = 0xb89
  val mhpmcounter10h = 0xb8a
  val mhpmcounter11h = 0xb8b
  val mhpmcounter12h = 0xb8c
  val mhpmcounter13h = 0xb8d
  val mhpmcounter14h = 0xb8e
  val mhpmcounter15h = 0xb8f
  val mhpmcounter16h = 0xb90
  val mhpmcounter17h = 0xb91
  val mhpmcounter18h = 0xb92
  val mhpmcounter19h = 0xb93
  val mhpmcounter20h = 0xb94
  val mhpmcounter21h = 0xb95
  val mhpmcounter22h = 0xb96
  val mhpmcounter23h = 0xb97
  val mhpmcounter24h = 0xb98
  val mhpmcounter25h = 0xb99
  val mhpmcounter26h = 0xb9a
  val mhpmcounter27h = 0xb9b
  val mhpmcounter28h = 0xb9c
  val mhpmcounter29h = 0xb9d
  val mhpmcounter30h = 0xb9e
  val mhpmcounter31h = 0xb9f
  val all = {
    val res = collection.mutable.ArrayBuffer[Int]()
    res += cycle
    res += instret
    res += hpmcounter3
    res += hpmcounter4
    res += hpmcounter5
    res += hpmcounter6
    res += hpmcounter7
    res += hpmcounter8
    res += hpmcounter9
    res += hpmcounter10
    res += hpmcounter11
    res += hpmcounter12
    res += hpmcounter13
    res += hpmcounter14
    res += hpmcounter15
    res += hpmcounter16
    res += hpmcounter17
    res += hpmcounter18
    res += hpmcounter19
    res += hpmcounter20
    res += hpmcounter21
    res += hpmcounter22
    res += hpmcounter23
    res += hpmcounter24
    res += hpmcounter25
    res += hpmcounter26
    res += hpmcounter27
    res += hpmcounter28
    res += hpmcounter29
    res += hpmcounter30
    res += hpmcounter31
    res += mstatus
    res += misa
    res += medeleg
    res += mideleg
    res += mie
    res += mtvec
    res += mscratch
    res += mepc
    res += mcause
    res += mtval
    res += mip
    res += tselect
    res += tdata1
    res += tdata2
    res += tdata3
    res += dcsr
    res += dpc
    res += dscratch
    res += mcycle
    res += minstret
    res += mhpmcounter3
    res += mhpmcounter4
    res += mhpmcounter5
    res += mhpmcounter6
    res += mhpmcounter7
    res += mhpmcounter8
    res += mhpmcounter9
    res += mhpmcounter10
    res += mhpmcounter11
    res += mhpmcounter12
    res += mhpmcounter13
    res += mhpmcounter14
    res += mhpmcounter15
    res += mhpmcounter16
    res += mhpmcounter17
    res += mhpmcounter18
    res += mhpmcounter19
    res += mhpmcounter20
    res += mhpmcounter21
    res += mhpmcounter22
    res += mhpmcounter23
    res += mhpmcounter24
    res += mhpmcounter25
    res += mhpmcounter26
    res += mhpmcounter27
    res += mhpmcounter28
    res += mhpmcounter29
    res += mhpmcounter30
    res += mhpmcounter31
    res += mucounteren
    res += mhpmevent3
    res += mhpmevent4
    res += mhpmevent5
    res += mhpmevent6
    res += mhpmevent7
    res += mhpmevent8
    res += mhpmevent9
    res += mhpmevent10
    res += mhpmevent11
    res += mhpmevent12
    res += mhpmevent13
    res += mhpmevent14
    res += mhpmevent15
    res += mhpmevent16
    res += mhpmevent17
    res += mhpmevent18
    res += mhpmevent19
    res += mhpmevent20
    res += mhpmevent21
    res += mhpmevent22
    res += mhpmevent23
    res += mhpmevent24
    res += mhpmevent25
    res += mhpmevent26
    res += mhpmevent27
    res += mhpmevent28
    res += mhpmevent29
    res += mhpmevent30
    res += mhpmevent31
    res += mvendorid
    res += marchid
    res += mimpid
    res += mhartid
    res.toArray
  }
  val all32 = {
    val res = collection.mutable.ArrayBuffer(all:_*)
    res += cycleh
    res += instreth
    res += hpmcounter3h
    res += hpmcounter4h
    res += hpmcounter5h
    res += hpmcounter6h
    res += hpmcounter7h
    res += hpmcounter8h
    res += hpmcounter9h
    res += hpmcounter10h
    res += hpmcounter11h
    res += hpmcounter12h
    res += hpmcounter13h
    res += hpmcounter14h
    res += hpmcounter15h
    res += hpmcounter16h
    res += hpmcounter17h
    res += hpmcounter18h
    res += hpmcounter19h
    res += hpmcounter20h
    res += hpmcounter21h
    res += hpmcounter22h
    res += hpmcounter23h
    res += hpmcounter24h
    res += hpmcounter25h
    res += hpmcounter26h
    res += hpmcounter27h
    res += hpmcounter28h
    res += hpmcounter29h
    res += hpmcounter30h
    res += hpmcounter31h
    res += mcycleh
    res += minstreth
    res += mhpmcounter3h
    res += mhpmcounter4h
    res += mhpmcounter5h
    res += mhpmcounter6h
    res += mhpmcounter7h
    res += mhpmcounter8h
    res += mhpmcounter9h
    res += mhpmcounter10h
    res += mhpmcounter11h
    res += mhpmcounter12h
    res += mhpmcounter13h
    res += mhpmcounter14h
    res += mhpmcounter15h
    res += mhpmcounter16h
    res += mhpmcounter17h
    res += mhpmcounter18h
    res += mhpmcounter19h
    res += mhpmcounter20h
    res += mhpmcounter21h
    res += mhpmcounter22h
    res += mhpmcounter23h
    res += mhpmcounter24h
    res += mhpmcounter25h
    res += mhpmcounter26h
    res += mhpmcounter27h
    res += mhpmcounter28h
    res += mhpmcounter29h
    res += mhpmcounter30h
    res += mhpmcounter31h
    res.toArray
  }
}


class CSRFileIO(implicit val conf: YSYX24100012Config) extends Bundle {
  val hartid = Input(UInt(conf.xprlen.W))
  val rw = new Bundle {
    val cmd = Input(UInt(CSR.SZ.W))
    val rdata = Output(UInt(conf.xprlen.W))
    val wdata = Input(UInt(conf.xprlen.W))
  }

  val csr_stall = Output(Bool())
  val eret = Output(Bool())
  // val singleStep = Output(Bool())

  val decode = new Bundle {
    val csr = Input(UInt(CSR.ADDRSZ.W))
    val read_illegal = Output(Bool())
    val write_illegal = Output(Bool())
    val system_illegal = Output(Bool())
  }

  val status = Output(new MStatus())
  val evec = Output(UInt(conf.xprlen.W))
  val exception = Input(Bool())
  val pc = Input(UInt(conf.xprlen.W))
  val retire = Input(Bool())
  // val time = Output(UInt(conf.xprlen.W))
  // val counters = Vec(60, new PerfCounterIO)

}

class CSRFile(implicit val conf: YSYX24100012Config) extends Module
{
  val io = IO(new CSRFileIO)
  io := DontCare

  val reset_mstatus = WireInit(0.U.asTypeOf(new MStatus()))
  reset_mstatus.mpp := PRV.M
  reset_mstatus.prv := PRV.M
  val reg_mstatus = RegInit(reset_mstatus)
  val reg_mepc = Reg(UInt(conf.xprlen.W))
  val reg_mcause = Reg(UInt(conf.xprlen.W))
  val reg_mtval = Reg(UInt(conf.xprlen.W))
  val reg_mtvec = Reg(UInt(conf.xprlen.W))
  // val reg_mscratch = Reg(UInt(conf.xprlen.W))
  // val reg_mtimecmp = Reg(UInt(conf.xprlen.W))
  // val reg_medeleg = Reg(UInt(conf.xprlen.W))

  // val reg_mip = RegInit(0.U.asTypeOf(new MIP()))
  // val reg_mie = RegInit(0.U.asTypeOf(new MIP()))
  // val reg_wfi = RegInit(false.B)

  // val reg_time = WideCounter(64)
  // val reg_instret = WideCounter(64, io.retire)

  // val reg_mcounteren = Reg(UInt(32.W))
  //val reg_hpmevent = io.counters.map(c => Reg(init = 0.asUInt(conf.xprlen.W)))
  //(io.counters zip reg_hpmevent) foreach { case (c, e) => c.eventSel := e }
  // val reg_hpmcounter = io.counters.map(c => WideCounter(CSR.hpmWidth, c.inc, reset = false))

  val new_prv = WireInit(reg_mstatus.prv)
  reg_mstatus.prv := new_prv

  // val reg_debug = RegInit(false.B)
  // val reg_dpc = Reg(UInt(conf.xprlen.W))
  // val reg_dscratch = Reg(UInt(conf.xprlen.W))
  // val reg_singleStepped = Reg(Bool())
  // val reset_dcsr = WireInit(0.U.asTypeOf(new DCSR()))
  // reset_dcsr.xdebugver := 1
  // reset_dcsr.prv := PRV.M
  // val reg_dcsr = RegInit(reset_dcsr)

  val system_insn = io.rw.cmd === CSR.I
  val cpu_ren = io.rw.cmd =/= CSR.N && !system_insn

  val read_mstatus = io.status.asUInt()
  val isa_string = "I"
  val misa = BigInt(0) | isa_string.map(x => 1 << (x - 'A')).reduce(_|_)
  val impid = 0x8000 // indicates an anonymous source, which can be used
                     // during development before a Source ID is allocated.

  val read_mapping = collection.mutable.LinkedHashMap[Int,Bits](
    // CSRs.mcycle -> reg_time,
    // CSRs.minstret -> reg_instret,
    // CSRs.mimpid -> 0.U,
    // CSRs.marchid -> 0.U,
    // CSRs.mvendorid -> 0.U,
    // CSRs.misa -> misa.U,
    // CSRs.mimpid -> impid.U,
    CSRs.mstatus -> read_mstatus,
    CSRs.mtvec -> MTVEC.U,
    // CSRs.mip -> reg_mip.asUInt(),
    // CSRs.mie -> reg_mie.asUInt(),
    // CSRs.mscratch -> reg_mscratch,
    CSRs.mepc -> reg_mepc,
    CSRs.mtval -> reg_mtval,
    CSRs.mcause -> reg_mcause,
    // CSRs.mhartid -> io.hartid,
    // CSRs.dcsr -> reg_dcsr.asUInt,
    // CSRs.dpc -> reg_dpc,
    // CSRs.dscratch -> reg_dscratch,
    // CSRs.medeleg -> reg_medeleg
    )

  // for (i <- 0 until CSR.nCtr)
  // {
  //   read_mapping += (i + CSR.firstMHPC) -> reg_hpmcounter(i)
  //   read_mapping += (i + CSR.firstMHPCH) -> reg_hpmcounter(i)
  // }

/*  for (((e, c), i) <- (reg_hpmevent.padTo(CSR.nHPM, 0.U)
                       zip reg_hpmcounter.map(x => x: UInt).padTo(CSR.nHPM, 0.U)) zipWithIndex) {
    read_mapping += (i + CSR.firstHPE) -> e // mhpmeventN
    read_mapping += (i + CSR.firstMHPC) -> c // mhpmcounterN
    if (conf.usingUser) read_mapping += (i + CSR.firstHPC) -> c // hpmcounterN
    if (conf.xprlen == 32) {
      read_mapping += (i + CSR.firstMHPCH) -> c // mhpmcounterNh
      if (conf.usingUser) read_mapping += (i + CSR.firstHPCH) -> c // hpmcounterNh
    }
  }
*/
  // if (conf.usingUser) {
  //   read_mapping += CSRs.mcounteren -> reg_mcounteren
  //   read_mapping += CSRs.cycle -> reg_time
  //   read_mapping += CSRs.instret -> reg_instret
  // }

  // if (conf.xprlen == 32) {
  //   read_mapping += CSRs.mcycleh -> 0.U //(reg_time >> 32)
  //   read_mapping += CSRs.minstreth -> 0.U //(reg_instret >> 32)
  //   if (conf.usingUser) {
  //     read_mapping += CSRs.cycleh -> 0.U //(reg_time >> 32)
  //     read_mapping += CSRs.instreth -> 0.U //(reg_instret >> 32)
  //   }
  // }

  val decoded_addr = read_mapping map { case (k, v) => k -> (io.decode.csr === k) }

  val priv_sufficient = reg_mstatus.prv >= io.decode.csr(9,8)
  val read_only = io.decode.csr(11,10).andR
  val cpu_wen = cpu_ren && io.rw.cmd =/= CSR.R && priv_sufficient
  val wen = cpu_wen && !read_only
  val wdata = readModifyWriteCSR(io.rw.cmd, io.rw.rdata, io.rw.wdata)

  val opcode = 1.U << io.decode.csr(2,0)
  val insn_call = system_insn && opcode(0)
  val insn_break = system_insn && opcode(1)
  val insn_ret = system_insn && opcode(2) && priv_sufficient
  val insn_wfi = system_insn && opcode(5) && priv_sufficient

  private def decodeAny(m: LinkedHashMap[Int,Bits]): Bool = m.map { case(k: Int, _: Bits) => io.decode.csr === k }.reduce(_||_)
  io.decode.read_illegal := reg_mstatus.prv < io.decode.csr(9,8) || !decodeAny(read_mapping) ||
    (io.decode.csr.inRange(CSR.firstCtr, CSR.firstCtr + CSR.nCtr) || io.decode.csr.inRange(CSR.firstCtrH, CSR.firstCtrH + CSR.nCtr)) ||
    !reg_debug
  io.decode.write_illegal := io.decode.csr(11,10).andR
  io.decode.system_illegal := reg_mstatus.prv < io.decode.csr(9,8)

  io.status := reg_mstatus

  io.eret := insn_call || insn_break || insn_ret

  // ILLEGAL INSTR
  // TODO: Support misaligned address exceptions
  when (io.exception) {
    reg_mcause := Causes.illegal_instruction
  }

  assert(PopCount(insn_ret :: io.exception :: Nil) <= 1, "these conditions must be mutually exclusive")

  //  when (reg_time >= reg_mtimecmp) {
  //     reg_mip.mtip := true
  //  }

  // io.evec must be held stable for more than one cycle for the
  // microcoded code to correctly redirect the PC on exceptions
  // ?????????? should be set to another value?
  io.evec := 0x80000004L.U

  //DRET
  when(insn_ret && io.decode.csr(10)){
    new_prv := reg_dcsr.prv
    reg_debug := false
    io.evec := reg_dpc
  }

  //MRET
  when (insn_ret && !io.decode.csr(10)) {
    reg_mstatus.mie := reg_mstatus.mpie
    reg_mstatus.mpie := true
    new_prv := reg_mstatus.mpp
    io.evec := reg_mepc
  }

  //ECALL
  when(insn_call){
    reg_mcause := reg_mstatus.prv + Causes.user_ecall
  }

  //EBREAK
  when(insn_break){
    reg_mcause := Causes.breakpoint
  }

  when (io.exception || insn_call || insn_break) {
    reg_mepc := io.pc
  }

  io.time := reg_time
  io.csr_stall := reg_wfi


  io.rw.rdata := Mux1H(for ((k, v) <- read_mapping) yield decoded_addr(k) -> v)

  when (wen) {
    // debug csr
    // when (decoded_addr(CSRs.dcsr)) {
    //     val new_dcsr = wdata.asTypeOf(new DCSR())
    //     reg_dcsr.step := new_dcsr.step
    //     reg_dcsr.ebreakm := new_dcsr.ebreakm
    //     if (conf.usingUser) reg_dcsr.ebreaku := new_dcsr.ebreaku
    //   }

    when (decoded_addr(CSRs.mstatus)) {
      val new_mstatus = wdata.asTypeOf(new MStatus())
      reg_mstatus.mie := new_mstatus.mie
      reg_mstatus.mpie := new_mstatus.mpie
    }
    // when (decoded_addr(CSRs.mip)) {
    //   val new_mip = wdata.asTypeOf(new MIP())
    //   reg_mip.msip := new_mip.msip
    // }
    // when (decoded_addr(CSRs.mie)) {
    //   val new_mie = wdata.asTypeOf(new MIP())
    //   reg_mie.msip := new_mie.msip
    //   reg_mie.mtip := new_mie.mtip
    // }
    // for (i <- 0 until CSR.nCtr)
    // {
    //   writeCounter(i + CSR.firstMHPC, reg_hpmcounter(i), wdata)
    // }
/*    for (((e, c), i) <- (reg_hpmevent zip reg_hpmcounter) zipWithIndex) {
      writeCounter(i + CSR.firstMHPC, c, wdata)
      //when (decoded_addr(i + CSR.firstHPE)) { e := perfEventSets.maskEventSelector(wdata) }
    }*/
    // writeCounter(CSRs.mcycle, reg_time, wdata)
    // writeCounter(CSRs.minstret, reg_instret, wdata)

    when (decoded_addr(CSRs.dpc))      { reg_dpc := wdata }
    // when (decoded_addr(CSRs.dscratch)) { reg_dscratch := wdata }

    when (decoded_addr(CSRs.mepc))     { reg_mepc := (wdata(conf.xprlen-1,0) >> 2.U) << 2.U }
    when (decoded_addr(CSRs.mcause))   { reg_mcause := wdata & ((BigInt(1) << (conf.xprlen-1)) + 31).U /* only implement 5 LSBs and MSB */ }
    when (decoded_addr(CSRs.mtval))    { reg_mtval := wdata(conf.xprlen-1,0) }
    // when (decoded_addr(CSRs.mscratch)) { reg_mscratch := wdata }
    // when (decoded_addr(CSRs.medeleg))    { reg_medeleg := wdata(conf.xprlen-1,0) }

    // if(conf.usingUser){
    //   when (decoded_addr(CSRs.cycleh))   { reg_time := wdata }
    //   when (decoded_addr(CSRs.instreth)) { reg_instret := wdata }
    // }
  }

  // if (!conf.usingUser) {
  //   reg_mcounteren := 0
  // }

  // def writeCounter(lo: Int, ctr: WideCounter, wdata: UInt) = {
  //   val hi = lo + CSRs.mcycleh - CSRs.mcycle
  //   when (decoded_addr(hi)) { ctr := Cat(wdata(ctr.getWidth-33, 0), ctr(31, 0)) }
  //   when (decoded_addr(lo)) { ctr := Cat(ctr(ctr.getWidth-1, 32), wdata) }
  // }
  def readModifyWriteCSR(cmd: UInt, rdata: UInt, wdata: UInt) =
    (Mux(cmd.isOneOf(CSR.S, CSR.C), rdata, 0.U) | wdata) & ~Mux(cmd === CSR.C, wdata, 0.U)
}
