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
    val intQueue = Module(new IntQueue())
    val memQueue = Module(new MemQueue())
    val is_memA = io.rm_dp.bits.instA.mem_ctrl.mem_val
    val is_memB = io.rm_dp.bits.instB.mem_ctrl.mem_val
    val is_csrA = io.rm_dp.bits.instA.csr_ctrl.csr_cmd =/= CSR.N 
    val is_csrB = io.rm_dp.bits.instB.csr_ctrl.csr_cmd =/= CSR.N
    val is_intA = !(is_memA || is_csrA)
    val is_intB = !(is_memB || is_csrB)

    io.rm_dp.ready := !intQueue.io.queue_full && !memQueue.io.queue_full

    intQueue.io.enqueue_a := Mux(is_intA, io.rm_dp.bits.instA,  WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    intQueue.io.enqueue_b := Mux(is_intB, io.rm_dp.bits.instB,  WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    intQueue.io.phyreg_states  := io.phyreg_states
    intQueue.io.redirect        := io.redirect

    memQueue.io.enqueue_a := Mux(!is_intA, io.rm_dp.bits.instA, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    memQueue.io.enqueue_b := Mux(!is_intB, io.rm_dp.bits.instB, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    memQueue.io.phyreg_states := io.phyreg_states
    memQueue.io.redirect := io.redirect

    io.dp_rr.valid := !io.redirect && (intQueue.io.dequeue_a.valid || intQueue.io.dequeue_b.valid )
    io.dp_rr.bits.instA := intQueue.io.dequeue_a
    io.dp_rr.bits.instB := intQueue.io.dequeue_b
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

  // 存储队列的寄存器堆，使用下划线命名
  val bank = RegInit(VecInit(Seq.fill(IQ_SIZE)(0.U.asTypeOf(new InstCtrlBlock()))))

  // 指针更名：EnQueuePointer -> enqueue_pointer, DeQueuePointer -> dequeue_pointer
  val enqueue_pointer = RegInit(0.U(IQ_BITS.W))        // 队尾指针
  val dequeue_pointer = RegInit(0.U(IQ_BITS.W))        // 队头指针
  
  // 队列满逻辑：更名queue_full
  io.queue_full := ((enqueue_pointer + 1.U) === dequeue_pointer) || 
                   ((enqueue_pointer + 2.U) === dequeue_pointer)

  // 出队选择逻辑：更名Select -> select, DeQueueReady -> dequeue_ready
  val select = bank(dequeue_pointer)
  val dequeue_ready = io.dequeue.ready
  io.dequeue.valid := !io.redirect && select.valid && (io.phyreg_states(select.prs1_addr) && io.phyreg_states(select.prs2_addr)) || 
    (io.phyreg_states(select.prs1_addr) && select.csr_ctrl.csr_cmd =/= CSR.N) ||
    (select.csr_ctrl.ebreak || select.csr_ctrl.eret)

  // 入队使能信号：更名Aenter -> a_enter, Benter -> b_enter
  val a_enter = !io.queue_full && io.enqueue_a.valid
  val b_enter = !io.queue_full && io.enqueue_b.valid

  // 回滚处理逻辑
  when(io.redirect) {
    enqueue_pointer := 0.U
    dequeue_pointer := 0.U
    for (i <- 0 until 16) {
      bank(i) := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
    }
    io.dequeue.bits := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
  }.otherwise {
    bank(enqueue_pointer) := Mux(a_enter, io.enqueue_a, WireInit(0.U.asTypeOf(new InstCtrlBlock())))
    bank(enqueue_pointer + 1.U) := Mux(b_enter, io.enqueue_b, WireInit(0.U.asTypeOf(new InstCtrlBlock())))

    // 出队逻辑
    when(io.dequeue.fire) {
      bank(dequeue_pointer) := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
    }
    io.dequeue.bits := Mux(io.dequeue.fire, select, WireInit(0.U.asTypeOf(new InstCtrlBlock())))

    // 更新指针
    enqueue_pointer := enqueue_pointer + a_enter.asUInt + b_enter.asUInt
    dequeue_pointer := dequeue_pointer + dequeue_ready.asUInt
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

  // 常量定义（保持大写，但使用下划线分隔）
  val index0_mask = "hFFFE".U(16.W)

  // 存储队列的寄存器堆
  val bank = RegInit(VecInit(Seq.fill(IQ_SIZE)(0.U.asTypeOf(new InstCtrlBlock()))))

  // 生成空闲列表：计算哪些条目为空闲（valid为false）
  def gen_free_list(): UInt = {
    val free_vec = Wire(Vec(IQ_SIZE, Bool()))
    for (i <- 0 until IQ_SIZE) {
      free_vec(i) := !bank(i).valid  
    }
    free_vec.asUInt & index0_mask  // 确保第0项永不空闲
  }

  // 生成就绪列表：计算哪些条目操作数就绪
  def gen_ready_list(): UInt = {
    val ready_vec = Wire(Vec(IQ_SIZE, Bool()))
    for (i <- 0 until IQ_SIZE) {
      val entry = bank(i)
      // 只有指令有效且两个源操作数就绪时才算就绪
      ready_vec(i) := entry.valid && 
                     io.phyreg_states(entry.prs1_addr) && 
                     io.phyreg_states(entry.prs2_addr)
    }
    ready_vec.asUInt & index0_mask  // 确保第0项永不就绪
  }

  // 使用PriorityEncoder高效查找最低设置位
  val free_list = gen_free_list()
  val ready_list = gen_ready_list()

  // 查找两个最低位的空闲索引（用于入队）
  val free_idx_a = PriorityEncoder(free_list)
  val free_list_after_a = free_list & ~(1.U << free_idx_a)  // 清除第一个找到的位
  val free_idx_b = PriorityEncoder(free_list_after_a)
  
  // 查找两个最低位的就绪索引（用于出队）
  val ready_idx_a = PriorityEncoder(ready_list)
  val ready_list_after_a = ready_list & ~(1.U << ready_idx_a)
  val ready_idx_b = PriorityEncoder(ready_list_after_a)

  // 队列满判断：检查是否至少有两个空闲条目
  io.queue_full := !free_list.orR || !free_list_after_a.orR

  // 回滚逻辑：清空整个bank
  when(io.redirect) {
    bank.foreach(_ := 0.U.asTypeOf(new InstCtrlBlock()))
    io.dequeue_a := 0.U.asTypeOf(new InstCtrlBlock())
    io.dequeue_b := 0.U.asTypeOf(new InstCtrlBlock())
  }.otherwise {
    // 入队逻辑：只有索引有效且非0时才写入
    when(free_idx_a =/= 0.U) { bank(free_idx_a) := io.enqueue_a }
    when(free_idx_b =/= 0.U) { bank(free_idx_b) := io.enqueue_b }

    // 出队逻辑：取出数据后清空对应条目
    when(ready_idx_a =/= 0.U) { 
      io.dequeue_a := bank(ready_idx_a)
      bank(ready_idx_a) := 0.U.asTypeOf(new InstCtrlBlock())  // 清空条目
    }.otherwise {
      io.dequeue_a := 0.U.asTypeOf(new InstCtrlBlock())
    }

    when(ready_idx_b =/= 0.U) { 
      io.dequeue_b := bank(ready_idx_b)
      bank(ready_idx_b) := 0.U.asTypeOf(new InstCtrlBlock())
    }.otherwise {
      io.dequeue_b := 0.U.asTypeOf(new InstCtrlBlock())
    }
  }
}
