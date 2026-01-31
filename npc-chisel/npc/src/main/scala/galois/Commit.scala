package npc.galois

import chisel3._
import chisel3.util._
import npc.common._
import npc._
import npc.galois.Constants._
import npc.common.UtilMethods.{ResultHoldBypass}

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
    val retireB = rob(dequeue_ptr + 1.U)
    val is_store = retireA.mem_ctrl.mem_val && retireA.mem_ctrl.mem_fcn === M_XWR
    val readyA = WireInit(false.B)
    retireA := rob(dequeue_ptr)
    readyA := retireA.valid && retireA.finish && (!is_store || (is_store && io.retire_store.fire))
    dontTouch(readyA)
    dontTouch(retireA)
    // branch, jump, exception only redirect after retire
    when (retireA.exception =/= EXC_NORMAL){
        retireA.pc_sel := PC_EXC 
    }
    io.redirect := readyA && ((retireA.bju_out.should_redirect) || retireA.pc_sel =/= PC_4)

    val retireB_exception   = retireB.exception =/= EXC_NORMAL
    val retireB_redirect    = retireB.pc_sel =/= PC_4
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