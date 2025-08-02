// package npc
// {
// import chisel3._
// import chisel3.util._

// import npc.common._
// import npc.Constants._

// class AluOpIo(implicit val conf: ysyx_24100012_Config) extends Bundle {
//     val in = Input(new AluOpIn())
//     val out = Output(new AluOpOut())
// }

// class AluOpIn (implicit val conf: ysyx_24100012_Config) extends Bundle {
//    val inst = Input(UInt(conf.xprlen.W))
//    val ctl = Input(new CtlToAluOpIo())
//    val reg_in = Flipped(new regToDatIo())
//    val pc = Input(UInt(conf.xprlen.W))
// }

// class AluOpOut(implicit val conf: ysyx_24100012_Config) extends Bundle {
//    val alu_op1 = Output(UInt(conf.xprlen.W))
//    val alu_op2 = Output(UInt(conf.xprlen.W))
// }


// class ysyx_24100012_AluOp(implicit val conf: ysyx_24100012_Config) extends Module {
//    // immediates
//    val imm_i = io.inst(31, 20) 
//    val imm_s = Cat(io.inst(31, 25), io.inst(11,7))
//    val imm_b = Cat(io.inst(31), io.inst(7), io.inst(30,25), io.inst(11,8))
//    val imm_u = io.inst(31, 12)
//    val imm_j = Cat(io.inst(31), io.inst(19,12), io.inst(20), io.inst(30,21))
//    val imm_z = Cat(Fill(27,0.U), io.inst(19,15))

//    // sign-extend immediates
//    val imm_i_sext = Cat(Fill(20,imm_i(11)), imm_i)
//    val imm_s_sext = Cat(Fill(20,imm_s(11)), imm_s)
//    val imm_b_sext = Cat(Fill(19,imm_b(11)), imm_b, 0.U)
//    val imm_u_sext = Cat(imm_u, Fill(12,0.U))
//    val imm_j_sext = Cat(Fill(11,imm_j(19)), imm_j, 0.U)

//    val alu_op1 = MuxCase(0.U, Seq(
//                (io.ctl.op1_sel === OP1_RS1) -> io.reg_in.rs1_data,
//                (io.ctl.op1_sel === OP1_IMU) -> imm_u_sext,
//                (io.ctl.op1_sel === OP1_IMZ) -> imm_z
//                )).asUInt

//    val alu_op2 = MuxCase(0.U, Seq(
//                (io.ctl.op2_sel === OP2_RS2) -> io.reg_in.rs2_data,
//                (io.ctl.op2_sel === OP2_PC)  -> io.in.pc,
//                (io.ctl.op2_sel === OP2_IMI) -> imm_i_sext,
//                (io.ctl.op2_sel === OP2_IMS) -> imm_s_sext
//                )).asUInt

// }
// }