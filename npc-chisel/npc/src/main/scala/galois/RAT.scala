
package npc.galois
import chisel3._
import chisel3.util._
import npc.common.{Config, MemPortIo}   
import npc.galois.Constants._


class RegMapStageIO(implicit val conf: Config) extends Bundle {
    
}

class RegMapDebug(implicit val conf: Config) extends Bundle {
    val assign_count = Output(UInt(conf.xlen.W))
}

class RegMapIO(implicit val conf: Config) extends OOOBundle {
    val dec_rm          = Flipped(DecoupledIO(new BlockLineIO()))
    val rm_dp           = new DecoupledIO(new BlockLineIO())
    val retireA         = Input(new InstCtrlBlock)
    val retireB         = Input(new InstCtrlBlock)
    val redirect        = Input(Bool())

    val cmtA = Input(new InstCtrlBlock)
    val cmtB = Input(new InstCtrlBlock)
    val cmtC = Input(new InstCtrlBlock)
    val cmtD = Input(new InstCtrlBlock)
    val cmtE = Input(new InstCtrlBlock)

    val rob_numA        = Input(UInt(ROB_BITS.W))
    val rob_numB        = Input(UInt(ROB_BITS.W))
    val rob_full        = Input(Bool())
    val phyreg_states   = Output(UInt(PRF_SIZE.W))
    val arch_regfile    = Output(Vec(ARC_SIZE,UInt(conf.xlen.W)))
    val debug           = Output(new RegMapDebug())
}

class RegMap (implicit val conf: Config)extends OOOModule { 

    val io = IO(new RegMapIO())
    val dec_rm_fire = io.dec_rm.fire
    val mapTable    = new RAT()
    val cmtTable    = new RAT()
    val prfCtrl     = new PhyRegCtrl()
    val queue       = Module(new Queue(new BlockLineIO, RMQ_SIZE,pipe = true,hasFlush = true))
    

    val instA = WireInit(0.U.asTypeOf(new InstCtrlBlock())) 
    val instB = WireInit(0.U.asTypeOf(new InstCtrlBlock())) 
    // instA := Mux(dec_rm_fire, io.dec_rm.bits.instA, 0.U.asTypeOf(new InstCtrlBlock()))
    // instB := Mux(dec_rm_fire, io.dec_rm.bits.instB, 0.U.asTypeOf(new InstCtrlBlock()))
    instA := Mux(queue.io.deq.valid, queue.io.deq.bits.instA, 0.U.asTypeOf(new InstCtrlBlock()))
    instB := Mux(queue.io.deq.valid, queue.io.deq.bits.instB, 0.U.asTypeOf(new InstCtrlBlock()))

    // make sure freeA freeB is 0 when the phy reg is allocated in this cycle
    val freeA = prfCtrl.read(prfCtrl.freePhyRegisterA) === 0.U
    val freeB = prfCtrl.read(prfCtrl.freePhyRegisterB) === 0.U
    val prsWbaddrA = Mux(instA.wbaddr =/= 0.U && freeA , prfCtrl.freePhyRegisterA, 0.U)
    val prsWbaddrB = Mux(instB.wbaddr =/= 0.U && freeB , prfCtrl.freePhyRegisterB, 0.U)
    val instAwen = instA.wb_ctrl.rf_wen 
    val instBwen = instB.wb_ctrl.rf_wen
    val AhasWb = instAwen && (prsWbaddrA =/= 0.U || instA.wbaddr === 0.U)
    val BhasWb = instBwen && (prsWbaddrB =/= 0.U || instB.wbaddr === 0.U)
    val AWBvalid = AhasWb || !instAwen
    val BWBvalid = BhasWb || !instBwen


    val cmtWbaddrA = mapTable.read(instA.wbaddr)
    val cmtWbaddrB = Mux(instA.wbaddr === instB.wbaddr,prsWbaddrA, mapTable.read(instB.wbaddr))

    queue.io.flush.get          := io.redirect
    queue.io.enq.valid          := dec_rm_fire
    queue.io.enq.bits           <> io.dec_rm.bits 
    queue.io.deq.ready          := io.rm_dp.ready && (AWBvalid && BWBvalid) && !prfCtrl.is_full() && !io.rob_full
    
    // solve RAW hazard in same block
    val prs1_addrA = WireInit(0.U)
    val prs2_addrA = WireInit(0.U)
    prs1_addrA := mapTable.read(instA.rs1_addr)
    prs2_addrA := mapTable.read(instA.rs2_addr)

    val prs1_addrB = Mux(instB.rs1_addr === instA.wbaddr && instAwen, prsWbaddrA, mapTable.read(instB.rs1_addr))
    val prs2_addrB = Mux(instB.rs2_addr === instA.wbaddr && instAwen, prsWbaddrA, mapTable.read(instB.rs2_addr))

    // solve WAW hazard in same block
    val retireA = io.retireA
    val retireB = io.retireB
    val arch_regfile    = new PhyRegFile(ARC_SIZE)
    io.phyreg_states    := prfCtrl.ready_list
    io.arch_regfile     := arch_regfile.regfile

    when(retireA.wbaddr =/= retireB.wbaddr){
        cmtTable.write(retireA.valid  && retireA.finish, retireA.wbaddr, retireA.prs_wbaddr)
        arch_regfile.write(retireA.valid && retireA.finish, retireA.wbaddr, retireA.wb_data)
    }
    cmtTable.write(retireB.valid && retireB.finish, retireB.wbaddr, retireB.prs_wbaddr)
    arch_regfile.write(retireB.valid && retireB.finish, retireB.wbaddr, retireB.wb_data)
    


    when (io.redirect){
        for (i <- 0 until ARC_SIZE) {
            // use cmt table rollback maptable
            // instA retired (a redirect inst) also should update maptable in one cycle
            when(i.U === retireA.wbaddr){
                mapTable.write(true.B, i.U, retireA.prs_wbaddr)
            } .otherwise{
                mapTable.write(true.B, i.U, cmtTable.read(i.U))
            }
        }
        val wen = retireA.wb_ctrl.rf_wen
        prfCtrl.rollback(wen,retireA.cmt_wbaddr, retireA.prs_wbaddr) 
        io.rm_dp.bits.instA := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
        io.rm_dp.bits.instB := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
    }.otherwise{
        when(instA.wbaddr =/= instB.wbaddr && instAwen){
            mapTable.write(queue.io.deq.fire && instA.valid, instA.wbaddr, prsWbaddrA)
        }
        mapTable.write(queue.io.deq.fire && instB.valid && instBwen, instB.wbaddr, prsWbaddrB)
        
        // to issue
        prfCtrl.write(prsWbaddrA =/= 0.U , prsWbaddrA, 1.U(2.W))
        prfCtrl.write(prsWbaddrB =/= 0.U , prsWbaddrB, 1.U(2.W))
        // to excuted
        prfCtrl.write(io.cmtA.valid && io.cmtA.finish,  io.cmtA.prs_wbaddr, 2.U(2.W))
        prfCtrl.write(io.cmtB.valid && io.cmtB.finish,  io.cmtB.prs_wbaddr, 2.U(2.W))
        prfCtrl.write(io.cmtC.valid && io.cmtC.finish,  io.cmtC.prs_wbaddr, 2.U(2.W))
        prfCtrl.write(io.cmtD.valid && io.cmtD.finish,  io.cmtD.prs_wbaddr, 2.U(2.W))
        prfCtrl.write(io.cmtE.valid && io.cmtE.finish,  io.cmtE.prs_wbaddr, 2.U(2.W))

        val instA_out = InstCtrlBlock.copy(instA,
            prs1_addr = Some(prs1_addrA),
            prs2_addr = Some(prs2_addrA),
            prs_wbaddr = Some(prsWbaddrA),
            cmt_wbaddr = Some(cmtWbaddrA),
            reorder_num = Some(io.rob_numA)
        )
        val instB_out = InstCtrlBlock.copy(instB,
            prs1_addr = Some(prs1_addrB),
            prs2_addr = Some(prs2_addrB),
            prs_wbaddr = Some(prsWbaddrB),
            cmt_wbaddr = Some(cmtWbaddrB),
            reorder_num = Some(io.rob_numB)
        )
        io.rm_dp.bits.instA := Mux(AWBvalid, instA_out, 0.U.asTypeOf(new InstCtrlBlock())) 
        io.rm_dp.bits.instB := Mux(BWBvalid, instB_out, 0.U.asTypeOf(new InstCtrlBlock()))
    }

    prfCtrl.write(retireA.valid && retireA.finish,  retireA.prs_wbaddr, 3.U(2.W))
    prfCtrl.write(retireB.valid && retireB.finish,  retireB.prs_wbaddr, 3.U(2.W))
    // free physical register 
    // cmt_wbaddr is the last wbaddr, when the inst come to the regMap stage,
    // so when instA retired, it should no inst read cmt_wbaddr as src,
    // because it should use instA.prs_wbaddr. so cmt_wbaddr can be free.
    prfCtrl.write(retireA.valid && retireA.finish,  retireA.cmt_wbaddr, 0.U(2.W))
    prfCtrl.write(retireB.valid && retireB.finish,  retireB.cmt_wbaddr, 0.U(2.W))

    io.rm_dp.valid  := queue.io.deq.fire 
    io.dec_rm.ready := queue.io.enq.ready 
    io.debug.assign_count := prfCtrl.count_assigned()
}

class RAT (implicit val conf: Config) extends  HasOOOParams {
    val table = RegInit(VecInit((0 until ARC_SIZE).map(i => i.U(PRF_BITS.W))))
    def read(addr: UInt) = table(addr)
    def write(wen: Bool, waddr: UInt, wdata: UInt): Unit = {
        when(wen) {
            table(waddr) := wdata
        }
    }
}