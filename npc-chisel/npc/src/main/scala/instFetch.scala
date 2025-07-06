package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc.Constants._

class InstFetchIo(implicit val conf: YSYX24100012Config) extends Bundle() {
  val imem = new MemPortIo(conf.xprlen)
  val targets = Flipped(new PCTargets())
  val inst = Output(UInt(conf.xprlen.W))
  val stall = Input(Bool())
  val pc_io = new PCIo()
}



class PCIo(implicit val conf: YSYX24100012Config) extends Bundle() {
  val pc_sel = Input(UInt(PC_4.getWidth.W))
  val pc_plus4 = Output(UInt(conf.xprlen.W))
  val pc =  Output(UInt(conf.xprlen.W))

}

class YSYX24100012InstFetch(implicit conf: YSYX24100012Config) extends Module {
  val io = IO(new InstFetchIo())
  io := DontCare
  val s_if :: s_exe :: s_lsu :: Nil = Enum(3)
  val state = RegInit(s_if)
  state := MuxLookup(state, s_if)(List(
    s_if       -> s_exe,
    s_exe      -> s_lsu,
    s_lsu      -> s_if,
  ))
  // Instruction Fetch
  val pc_next = Wire(UInt(conf.xprlen.W))

  // PC Register
  pc_next := MuxCase(io.pc_io.pc_sel, Seq(
                    (io.pc_io.pc_sel === PC_4)   -> io.pc_io.pc_plus4,
                    (io.pc_io.pc_sel === PC_BR)  -> io.targets.br_target,
                    (io.pc_io.pc_sel === PC_J )  -> io.targets.jmp_target,
                    (io.pc_io.pc_sel === PC_JR)  -> io.targets.jump_reg_target,
                    (io.pc_io.pc_sel === PC_EXC) -> io.targets.exception_target
                    ))

  val reg_pc_next = RegNext(pc_next)

  val pc_reg = RegInit(START_ADDR)
  when(io.imem.resp.valid && !io.stall) {
      pc_reg := reg_pc_next
  }
  
  io.imem.req.valid := MuxCase(
    state,
    Seq(
      (state === s_if) -> true.B,
      (state === s_exe || state === s_lsu) -> false.B,
  ))

  // Memory Requests
  io.imem.req.bits.addr := pc_reg
  io.imem.req.bits.fcn := M_XRD
  io.imem.req.bits.typ := MT_WU

  // Instruction Read
  io.inst := Mux(io.imem.resp.valid, io.imem.resp.bits.data, BUBBLE)
  
  io.pc_io.pc_plus4 := (pc_reg + 4.asUInt(conf.xprlen.W)) 
  io.pc_io.pc := pc_reg             
}