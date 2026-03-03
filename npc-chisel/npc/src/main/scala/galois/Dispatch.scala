package npc.galois

import chisel3._
import chisel3.util._

import npc.common.Instructions._
import npc.common._
import npc._
import npc.galois.Constants._

class DispatchIO (implicit val conf: Config) extends OOOBundle {
    val rm_dp           = Flipped(new DecoupledIO(new BlockLineIO()))
    val dp_rr           = new DecoupledIO(new BlockLineIO)
    val dp_rr_mem       = new DecoupledIO(new InstCtrlBlock())
    val phyreg_states   = Input(UInt(PRF_SIZE.W))
    val redirect        = Input(Bool())
}

class Dispatch (implicit val conf: Config) extends OOOModule { 
    val io = IO(new DispatchIO())

    val rm_dp_fire = (io.rm_dp.fire)
    val intQueue = Module(new IntQueue())
    val memQueue = Module(new MemQueue())
    val is_memA = io.rm_dp.bits.instA.mem_ctrl.mem_val
    val is_memB = io.rm_dp.bits.instB.mem_ctrl.mem_val
    val is_csrA = io.rm_dp.bits.instA.csr_ctrl.csr_cmd =/= CSR.N 
    val is_csrB = io.rm_dp.bits.instB.csr_ctrl.csr_cmd =/= CSR.N
    val is_intA = !(is_memA || is_csrA)
    val is_intB = !(is_memB || is_csrB)

    io.rm_dp.ready := !intQueue.io.queue_full && !memQueue.io.queue_full

    intQueue.io.enqueue_a := Mux(is_intA && rm_dp_fire, io.rm_dp.bits.instA,  WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    intQueue.io.enqueue_b := Mux(is_intB && rm_dp_fire, io.rm_dp.bits.instB,  WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    intQueue.io.phyreg_states  := io.phyreg_states
    intQueue.io.redirect        := io.redirect

    memQueue.io.enqueue_a := Mux(!is_intA && rm_dp_fire, io.rm_dp.bits.instA, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    memQueue.io.enqueue_b := Mux(!is_intB && rm_dp_fire, io.rm_dp.bits.instB, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    memQueue.io.phyreg_states := io.phyreg_states
    memQueue.io.redirect := io.redirect

    io.dp_rr.valid := !io.redirect && (intQueue.io.dequeue_a.valid || intQueue.io.dequeue_b.valid )
    io.dp_rr.bits.instA := intQueue.io.dequeue_a
    io.dp_rr.bits.instB := intQueue.io.dequeue_b
    io.dp_rr_mem.valid  := !io.redirect && memQueue.io.dequeue.valid
    io.dp_rr_mem <> memQueue.io.dequeue
}

class MemQueue(implicit val conf: Config) extends OOOModule {
  val io = IO(new Bundle {
    val enqueue_a = Input(new InstCtrlBlock)      
    val enqueue_b = Input(new InstCtrlBlock)      
    val dequeue   = new DecoupledIO((new InstCtrlBlock))       

    val phyreg_states = Input(UInt(PRF_SIZE.W))
    val queue_full = Output(Bool())               
    val redirect = Input(Bool())                  
  })

  val bank = RegInit(VecInit(Seq.fill(IQ_SIZE)(0.U.asTypeOf(new InstCtrlBlock()))))

  val enqueue_pointer = RegInit(0.U(IQ_BITS.W))        // 队尾指针
  val dequeue_pointer = RegInit(0.U(IQ_BITS.W))        // 队头指针
  
  io.queue_full := ((enqueue_pointer + 1.U) === dequeue_pointer) || 
                   ((enqueue_pointer + 2.U) === dequeue_pointer)

  val select = bank(dequeue_pointer)
  val dequeue_ready = io.dequeue.ready
  io.dequeue.valid := !io.redirect && select.valid &&
    (((io.phyreg_states(select.prs1_addr) || !select.alu_ctrl.rs1_oen || select.rs1_addr === 0.U ) &&
    (io.phyreg_states(select.prs2_addr)   || !select.alu_ctrl.rs2_oen || select.rs2_addr === 0.U )) || 
    ((io.phyreg_states(select.prs1_addr)  || !select.alu_ctrl.rs1_oen ) && select.csr_ctrl.csr_cmd =/= CSR.N) ||
    (select.csr_ctrl.ebreak || select.csr_ctrl.eret))

  val a_enter = !io.queue_full && io.enqueue_a.valid
  val b_enter = !io.queue_full && io.enqueue_b.valid

  when(io.redirect) {
    enqueue_pointer := 0.U
    dequeue_pointer := 0.U
    for (i <- 0 until IQ_SIZE) {
      bank(i) := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
    }
    io.dequeue.bits := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
  }.otherwise {
    when(a_enter){
      bank(enqueue_pointer) := Mux(a_enter, io.enqueue_a, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
      bank(enqueue_pointer + 1.U) := Mux(b_enter, io.enqueue_b, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    }.elsewhen(b_enter && !a_enter){
      bank(enqueue_pointer ) := Mux(b_enter, io.enqueue_b, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    }

    when(io.dequeue.fire) {
      bank(dequeue_pointer) := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
    }
    io.dequeue.bits := Mux(io.dequeue.fire, select, WireInit(0.U.asTypeOf(new InstCtrlBlock())))

    dequeue_pointer := dequeue_pointer + io.dequeue.fire.asUInt
    enqueue_pointer := enqueue_pointer + a_enter.asUInt + b_enter.asUInt
  }
}

class IntQueue(implicit val conf: Config) extends OOOModule {
  val io = IO(new Bundle {
    val enqueue_a = Input(new InstCtrlBlock)
    val enqueue_b = Input(new InstCtrlBlock)
    val dequeue_a = Output(new InstCtrlBlock)
    val dequeue_b = Output(new InstCtrlBlock)
    val phyreg_states = Input(UInt(PRF_SIZE.W))
    val redirect = Input(Bool())
    val queue_full = Output(Bool())
  })

  val index0_mask = "hFFFE".U(16.W)

  val bank = RegInit(VecInit(Seq.fill(IQ_SIZE)(0.U.asTypeOf(new InstCtrlBlock()))))

  def gen_free_list(): UInt = {
    val free_vec = Wire(Vec(IQ_SIZE, Bool()))
    for (i <- 0 until IQ_SIZE) {
      free_vec(i) := !bank(i).valid  
    }
    dontTouch(free_vec)
    free_vec.asUInt & index0_mask  // 确保第0项永不空闲
  }

  def gen_ready_list(): UInt = {
    val ready_vec = Wire(Vec(IQ_SIZE, Bool()))
    for (i <- 0 until IQ_SIZE) {
      val entry = bank(i)
      ready_vec(i) := entry.valid && 
                     (!entry.alu_ctrl.rs1_oen || entry.rs1_addr === 0.U || (entry.alu_ctrl.rs1_oen && io.phyreg_states(entry.prs1_addr))) &&
                      (!entry.alu_ctrl.rs2_oen || entry.rs2_addr === 0.U || io.phyreg_states(entry.prs2_addr))
    }
    dontTouch(ready_vec)
    ready_vec.asUInt & index0_mask  // 确保第0项永不就绪
  }

  val free_list = gen_free_list()
  val ready_list = gen_ready_list()

  val free_idx_a = Log2(lowbit(free_list))
  val free_list_after_a = free_list - lowbit(free_list)  // 清除第一个找到的位
  val free_idx_b = Log2(lowbit(free_list_after_a))
  
  val ready_idx_a = Log2(lowbit(ready_list))
  val ready_list_after_a = ready_list - lowbit(ready_list)
  val ready_idx_b = Log2(lowbit(ready_list_after_a))

  io.queue_full := !free_list.orR || !free_list_after_a.orR

  when(io.redirect) {
    bank.foreach(_ := 0.U.asTypeOf(new InstCtrlBlock()))
    io.dequeue_a := 0.U.asTypeOf(new InstCtrlBlock())
    io.dequeue_b := 0.U.asTypeOf(new InstCtrlBlock())
  }.otherwise {
    when(free_idx_a =/= 0.U ) { bank(free_idx_a) := io.enqueue_a }
    when(free_idx_b =/= 0.U ) { bank(free_idx_b) := io.enqueue_b }

    when(ready_idx_a =/= 0.U ) { 
      io.dequeue_a := bank(ready_idx_a)
      bank(ready_idx_a) := 0.U.asTypeOf(new InstCtrlBlock())  // 清空条目
    }.otherwise {
      io.dequeue_a := 0.U.asTypeOf(new InstCtrlBlock())
    }

    when(ready_idx_b =/= 0.U ) { 
      io.dequeue_b := bank(ready_idx_b)
      bank(ready_idx_b) := 0.U.asTypeOf(new InstCtrlBlock())
    }.otherwise {
      io.dequeue_b := 0.U.asTypeOf(new InstCtrlBlock())
    }
  }
}
