package npc.galois

import chisel3._
import chisel3.util._
import npc.common._
import npc._
import npc.galois.Constants._
import npc.common.UtilMethods.{ResultHoldBypass}
import javax.naming.spi.DirStateFactory.Result

class CommitDebug  (implicit val conf: Config) extends Bundle {
    val pcA                 = Output(UInt(conf.xlen.W))
    val pcB                 = Output(UInt(conf.xlen.W))
    val instA               = Output(UInt(32.W))
    val instB               = Output(UInt(32.W))
    val readyA              = Output(Bool())
    val readyB              = Output(Bool())
    val next_pc             = Output(UInt(conf.xlen.W))
    val mem_val             = Output(Bool())
    val mem_addr            = Output(UInt(conf.xlen.W))
}

class Commit (implicit val conf: Config) extends OOOModule{
    val io = IO(new Bundle{
        val rm_rob          = Flipped(DecoupledIO(new BlockLineIO))
        val retireB         = new InstCtrlBlock
        val retireA         = new InstCtrlBlock

        val retire_store    = new DecoupledIO((new InstCtrlBlock))
        val cmtA            = Input(new InstCtrlBlock)
        val cmtB            = Input(new InstCtrlBlock)
        val cmtC            = Input(new InstCtrlBlock)
        val cmtD            = Input(new InstCtrlBlock)
        val cmtE            = Input(new InstCtrlBlock)
        val cmtF            = Input(new InstCtrlBlock)
        val cmtG            = Input(new InstCtrlBlock)

        val rob_numA = Output(UInt(ROB_BITS.W))
        val rob_numB = Output(UInt(ROB_BITS.W))
        val redirect = Output(Bool())

        val forward_load    = Input(new InstCtrlBlock())
        val forward_store   = Output(new InstCtrlBlock())
        val debug            = new CommitDebug
	})
    // Pointer
    val enqueue_ptr = RegInit(0.U(ROB_BITS.W)) 
    val dequeue_ptr = RegInit(0.U(ROB_BITS.W)) 
    val rob_full = ((enqueue_ptr + 1.U ) === dequeue_ptr) || ((enqueue_ptr + 2.U ) === dequeue_ptr)
    io.rm_rob.ready := !rob_full
    io.rob_numA := enqueue_ptr
    io.rob_numB := enqueue_ptr + 1.U

    val rob = RegInit(VecInit(Seq.fill(ROB_SIZE)(WireInit(0.U.asTypeOf(new InstCtrlBlock())))))
    val retireA = WireInit(0.U.asTypeOf(new InstCtrlBlock()))
    val retireB = WireInit(0.U.asTypeOf(new InstCtrlBlock()))
    val is_store = retireA.mem_ctrl.mem_val && retireA.mem_ctrl.mem_fcn === M_XWR
    val readyA = WireInit(false.B)
    retireA := rob(dequeue_ptr)
    retireB := rob(dequeue_ptr + 1.U)
    readyA := retireA.valid && retireA.finish && (!is_store || (is_store && io.retire_store.fire))
    dontTouch(readyA)
    dontTouch(retireA)
    dontTouch(retireB)
    // branch, jump, exception only redirect after retire
    when (retireA.exception =/= EXC_NORMAL){
        retireA.pc_sel := PC_EXC 
    }
    io.redirect := readyA && ((retireA.bju_out.should_redirect) || retireA.pc_sel =/= PC_4)

    val retireB_exception   = retireB.exception =/= EXC_NORMAL
    val retireB_redirect    = retireB.bju_out.should_redirect || retireB.pc_sel =/= PC_4
    val retireB_store       = retireB.mem_ctrl.mem_val 
    val readyB = readyA && retireB.valid && retireB.finish && ~io.redirect && ~retireB_exception && ~retireB_redirect && ~retireB_store
    
    io.retireA      := Mux(readyA, retireA, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    io.retireB      := Mux(readyB, retireB, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    io.retire_store.valid   := is_store && retireA.valid && retireA.finish
    io.retire_store.bits    := Mux(is_store,  retireA, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    val Aenter = !rob_full && io.rm_rob.bits.instA.valid
    val Benter = Aenter && io.rm_rob.bits.instB.valid

    when(io.redirect){
        for(i <- 0 to ROB_SIZE-1){
            rob(i) := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
        }
        enqueue_ptr := 0.U
        dequeue_ptr := 0.U
    }.otherwise{
        when(~rob_full){ rob(enqueue_ptr) := io.rm_rob.bits.instA }
        when(~rob_full){ rob(enqueue_ptr + 1.U) := io.rm_rob.bits.instB }
        when(io.cmtA.valid && io.cmtA.finish){ rob(io.cmtA.reorder_num) := io.cmtA }
        when(io.cmtB.valid && io.cmtB.finish){ rob(io.cmtB.reorder_num) := io.cmtB }
        when(io.cmtC.valid && io.cmtC.finish){ rob(io.cmtC.reorder_num) := io.cmtC }
        when(io.cmtD.valid && io.cmtD.finish){ rob(io.cmtD.reorder_num) := io.cmtD }
        when(io.cmtE.valid && io.cmtE.finish){ rob(io.cmtE.reorder_num) := io.cmtE }
        when(io.cmtF.valid && io.cmtF.finish){ rob(io.cmtF.reorder_num) := io.cmtF }
        when(io.cmtG.valid && io.cmtG.finish){ rob(io.cmtG.reorder_num) := io.cmtG }

        enqueue_ptr := enqueue_ptr + Aenter.asUInt + Benter.asUInt
        dequeue_ptr := dequeue_ptr + readyA.asUInt + readyB.asUInt
        when(readyA){ rob(dequeue_ptr) := WireInit(0.U.asTypeOf(new InstCtrlBlock())) }
        when(readyB){ rob(dequeue_ptr + 1.U) := WireInit(0.U.asTypeOf(new InstCtrlBlock())) }
    }

    val hitVector = GenHitVec()
    val hit = hitVector =/= 0.U
    val circularHitVec = VecInit.tabulate(ROB_SIZE) { j =>
    val index = Mux(j.U < ROB_SIZE.U - enqueue_ptr, enqueue_ptr + j.U, j.U - (ROB_SIZE.U - enqueue_ptr))
        hitVector(index)
    }.asUInt

    val relIndex = PriorityEncoder(circularHitVec)
    val hitIndex = Mux(hit, (enqueue_ptr + relIndex) % ROB_SIZE.U, 0.U)

    io.forward_store := Mux(hit, rob(hitIndex), 0.U.asTypeOf(new InstCtrlBlock()))

    def GenHitVec(): UInt = {
        VecInit((0 until ROB_SIZE).map { i =>
            val is_store = rob(i).mem_ctrl.mem_val && rob(i).mem_ctrl.mem_fcn === M_XWR
            val addr_match = io.forward_load.valid && 
                            (io.forward_load.alu_out(conf.xprlen-1,2) === rob(i).alu_out(conf.xprlen-1,2))
            rob(i).valid && is_store && addr_match
        }).asUInt
    }

    /////// debug
    val debug = WireInit(0.U.asTypeOf(new CommitDebug))
    debug.pcA    := retireA.pc
    debug.pcB    := retireB.pc
    debug.readyA := readyA
    debug.readyB := readyB
    debug.instA    := retireA.inst
    debug.instB    := retireB.inst
    debug.mem_val  := retireA.mem_ctrl.mem_val
    debug.mem_addr := retireA.alu_out
    val regDebug = RegNext(debug)
    val reg_target = RegNext(retireA.target)
    val reg_redirect = RegNext(retireA.pc_sel =/= PC_4)
    io.debug.pcA        := regDebug.pcA
    io.debug.pcB        := regDebug.pcB
    io.debug.readyA     := regDebug.readyA
    io.debug.readyB     := regDebug.readyB
    io.debug.instA      := regDebug.instA
    io.debug.instB      := regDebug.instB
    io.debug.mem_val    := regDebug.mem_val
    io.debug.mem_addr   := regDebug.mem_addr
    // retireA valid should handle almost all cases,
    // if retireA is not valid, then all other rob invalid , 
    // should speculate based on last retired
    // last retireB valid should speculate retireB + 4
    // last retireB invalid should speculate retireA + 4
    io.debug.next_pc    := Mux(reg_redirect, reg_target,   
                            Mux(retireA.valid,retireA.pc ,
                            Mux(regDebug.readyB, regDebug.pcB+4.U, regDebug.pcA + 4.U)))
                                
}

//input : 00101010110
//output: 00100000000
object highbit {
    def apply(data: UInt): UInt = {
        Reverse(lowbit(Reverse(data)))
    }
}

//input : 00101010110
//output: 00000000010
object lowbit {
    def apply(data: UInt): UInt = {
        data & (~data+1.U)
    }
}