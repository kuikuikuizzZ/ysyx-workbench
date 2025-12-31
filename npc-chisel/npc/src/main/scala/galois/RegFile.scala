package npc.galois
import chisel3._
import chisel3.util._
import npc.common.{Config, MemPortIo}   
import npc.galois.Constants._


class PhyRegReadIO(implicit val conf: Config) extends Bundle {
  val valid = Input(Bool())
  val addr = Input(UInt(7.W))
  val data = Output(UInt(conf.xlen.W))
}

class PhyRegWriteIO(implicit val conf: Config) extends Bundle {
  val valid = Input(Bool())
  val addr = Input(UInt(7.W))
  val wdata = Output(UInt(conf.xlen.W))
}

class PhyFileIO(implicit val conf: Config) extends Bundle {
  // 4 read ports and 2 write ports for 2 issue
  val readA   = new PhyRegReadIO
  val readB   = new PhyRegReadIO
  val readC   = new PhyRegReadIO
  val readD   = new PhyRegReadIO
  val writeA  = new PhyRegWriteIO
  val writeB  = new PhyRegWriteIO
}

class PhyRegFile(size: Int)(implicit val conf: Config) {

  // Register File
  val regfile =  RegInit(VecInit(Seq.fill(size)(0.U(conf.xlen.W)))).suggestName("regfile_mem") 
  def read(addr: UInt): UInt = {
    Mux(addr =/= 0.U, regfile(addr), 0.asUInt(conf.xlen.W))
  }
  def write(wen: Bool, addr: UInt, data: UInt): Unit = { 
      when(wen && addr =/= 0.U){ regfile(addr) := data }
  }
}


// class PhyRegFile(implicit val conf: Config) extends Module {
//   val io = IO( new PhyFileIO())
//   io := DontCare
//   // Register File
//   val regfile = RegInit(16, UInt(conf.xlen.W)).suggestName("regfile_mem") 

//   when (io.writeA.valid && (io.writeA.addr =/= 0.U)) {
//     regfile(io.writeA.addr) := io.writeA.wdata
//   }
//   when (io.writeB.valid && (io.writeB.addr =/= 0.U)) {
//     regfile(io.writeB.addr) := io.writeB.wdata
//   }

//   io.readA.data := Mux((io.readA.addr =/= 0.U), regfile(io.readA.addr), 0.asUInt(conf.xlen.W))
//   io.readB.data := Mux((io.readB.addr =/= 0.U), regfile(io.readB.addr), 0.asUInt(conf.xlen.W))
//   io.readC.data := Mux((io.readC.addr =/= 0.U), regfile(io.readC.addr), 0.asUInt(conf.xlen.W))
//   io.readD.data := Mux((io.readD.addr =/= 0.U), regfile(io.readD.addr), 0.asUInt(conf.xlen.W))
// }

// class PhyRegStateCtrl(implicit val conf: Config) extends Module { 
//     val freeIdxA    = Output(UInt(PRF_BITS.W))
//     val freeIdxB    = Output(UInt(PRF_BITS.W))
//     val ready_list  = Output(UInt(PRF_SIZE.W))
// }

class PhyRegCtrl(implicit val conf: Config) extends HasOOOParams {
  object State {
    val Free     = 0.U(2.W)  // 00: 空闲
    val Mapped   = 1.U(2.W)  // 01: 已映射
    val Executed = 2.U(2.W)  // 10: 已执行
    val Assigned = 3.U(2.W)  // 11: 已分配
  }
  
  // 使用函数式编程初始化序列
  val seqInit = VecInit(
    Seq.tabulate(PRF_SIZE) { i =>
      if (i < ARC_SIZE) State.Assigned else State.Free
    }
  )
  
  val state = RegInit(seqInit)

  // 使用 PriorityEncoder 替代自定义位操作
  private def genFreeList: UInt = {
    VecInit(state.map(_ === State.Free)).asUInt
  }
  
  private def findFirstFree(bitVector: UInt): UInt = {
    PriorityEncoder(bitVector)
  }
  
  //NOTE: should be optimize by bypass network 
  def freePhyRegisterA: UInt = findFirstFree(genFreeList)
  
  def freePhyRegisterB: UInt = {
    val freeList = genFreeList
    val firstFree = freePhyRegisterA
    // 清除第一个空闲位后查找第二个
    val remainingFree = freeList & ~(1.U << firstFree)
    findFirstFree(remainingFree)
  }
  
  def ready_list: UInt = {
    VecInit(state.map(s => s(1))).asUInt
  }

  def write(wen: Bool, addr: UInt, data: UInt): Unit = { 
      when(wen && addr =/= 0.U){ state(addr) := data }
  }

  def rollback(freeReg: UInt, assignReg: UInt): Unit = {
    val newStates = VecInit(state.zipWithIndex.map { case (state, idx) =>
      val idxUint = idx.U
      MuxCase(
        Mux(state === State.Assigned, State.Assigned, State.Free), // 默认情况
        Seq(
          (idxUint === freeReg) -> State.Free,
          (idxUint === assignReg) -> State.Assigned
        )
      )
    })
    // 批量更新寄存器状态
    state := newStates
  }

}