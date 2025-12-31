
package npc.galois
import chisel3._
import chisel3.util._
import npc.common.{Config, MemPortIo}   
import npc.galois.Constants._
import npc.common._

class RegRead (implicit val conf: Config)extends OOOModule{
    val io = IO(new Bundle{
        val dp_rr       = Flipped(new DecoupledIO(new BlockLineIO()))
        val dp_rr_mem   = Flipped(new DecoupledIO(new InstCtrlBlock()))
        val rr_exe      = new DecoupledIO(new BlockLineIO())
        val rr_exe_mem  = new DecoupledIO(new InstCtrlBlock())

        val cmtA = Output(new InstCtrlBlock)
        val cmtB = Output(new InstCtrlBlock)
        val cmtC = Input(new InstCtrlBlock)
        val cmtD = Input(new InstCtrlBlock)
        val cmtE = Input(new InstCtrlBlock)

        val redirect = Input(Bool())
	})


    val PhyRegFile = new PhyRegFile(PRF_SIZE)
    val inA = io.dp_rr.bits.instA
    val inB = io.dp_rr.bits.instB
    val inC = io.dp_rr_mem.bits

    val src1 = PhyRegFile.read(inA.prs1_addr)
    val src2 = PhyRegFile.read(inA.prs2_addr)
    val src3 = PhyRegFile.read(inB.prs1_addr)
    val src4 = PhyRegFile.read(inB.prs2_addr)
    val src5 = PhyRegFile.read(inC.prs1_addr)
    val src6 = PhyRegFile.read(inC.prs2_addr)

    PhyRegFile.write(io.cmtA.valid && io.cmtA.finish, io.cmtA.prs_wbaddr, io.cmtA.wb_data)
    PhyRegFile.write(io.cmtB.valid && io.cmtB.finish, io.cmtB.prs_wbaddr, io.cmtB.wb_data)
    PhyRegFile.write(io.cmtC.valid && io.cmtC.finish, io.cmtC.prs_wbaddr, io.cmtC.wb_data)
    PhyRegFile.write(io.cmtD.valid && io.cmtD.finish, io.cmtD.prs_wbaddr, io.cmtD.wb_data)
    PhyRegFile.write(io.cmtE.valid && io.cmtE.finish, io.cmtE.prs_wbaddr, io.cmtE.wb_data)

    val instA = InstCtrlBlock.copy( base     = inA,
                                    rs1_data = Some(src1),
                                    rs2_data = Some(src2))
    val instB = InstCtrlBlock.copy( base     = inB,
                                    rs1_data = Some(src3),
                                    rs2_data = Some(src4))
    val instC = InstCtrlBlock.copy( base     = inC,
                                    rs1_data = Some(src5),  
                                    rs2_data = Some(src6))                                        
    val is_bjuA = instA.br_ctrl.br_type =/= BR_N
    val is_bjuB = instB.br_ctrl.br_type =/= BR_N
    val bju1 = Module(new BJU)
    val bju2 = Module(new BJU)
    val bju1_out = bju1.io.out
    val bju2_out = bju2.io.out
    bju1.io.inst := Mux(is_bjuA, instA, 0.U.asTypeOf(new InstCtrlBlock()))
    bju2.io.inst := Mux(is_bjuB, instB, 0.U.asTypeOf(new InstCtrlBlock()))


    val is_csrA = instA.csr_ctrl.csr_cmd =/= CSR.N
    val is_csrB = instB.csr_ctrl.csr_cmd =/= CSR.N
    val is_memA = instA.mem_ctrl.mem_val
    val is_memB = instB.mem_ctrl.mem_val

    val Afinish = instA.br_ctrl.br_type =/= BR_N 
    val Bfinish = instB.br_ctrl.br_type =/= BR_N 

    val cmtA = Mux(!Afinish, 0.U.asTypeOf(new InstCtrlBlock()), bju1_out)
    val cmtB = Mux(!Bfinish, 0.U.asTypeOf(new InstCtrlBlock()), bju2_out)                

    when(io.redirect){
        io.rr_exe.valid := false.B
        io.rr_exe.bits := WireInit(0.U.asTypeOf(new BlockLineIO()))
        io.rr_exe_mem.valid := false.B
        io.rr_exe_mem.bits := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
        io.cmtA := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
        io.cmtB := WireInit(0.U.asTypeOf(new InstCtrlBlock()))
    }.otherwise{
        io.rr_exe.valid := io.dp_rr.valid
        io.rr_exe.bits.instA    := Mux(!(is_bjuA || is_csrA || is_memA), instA , 0.U.asTypeOf(new InstCtrlBlock()))
        io.rr_exe.bits.instB    := Mux(!(is_bjuB || is_csrB || is_memB), instB , 0.U.asTypeOf(new InstCtrlBlock()))
        io.rr_exe_mem.valid := io.dp_rr_mem.valid
        io.rr_exe_mem.bits      := instC
        io.cmtA := InstCtrlBlock.copy(base = (cmtA),
                                        finish = Some(Afinish))
        io.cmtB := InstCtrlBlock.copy(base = (cmtB),
                                        finish = Some(Bfinish))
    }
    io.dp_rr.ready := io.rr_exe.ready
    io.dp_rr_mem.ready := io.rr_exe_mem.ready
}   