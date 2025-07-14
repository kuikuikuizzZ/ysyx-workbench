
package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._
import javax.xml.transform.OutputKeys

class LsuToWBIo(implicit val conf: YSYX24100012Config) extends Bundle {
    val data = Output(UInt(conf.xprlen.W))
}

class YSYX2400012LSU(implicit val conf: YSYX24100012Config) extends Module {
    val io = IO(new Bundle {
        val ctl = Flipped(new CtlToLSUIo())
        val dmem = new MemPortIo(conf.xprlen)
        val exe = Flipped(new exeToLSUIo())
        val pc_io = Flipped(new PCOut())
        val wb = new LsuToWBIo()
        val stall = Output(Bool())
        val ls_valid = Output(Bool())
    })
    io := DontCare

    io.dmem.req.valid    := io.ctl.mem_en
    io.dmem.req.bits.fcn := io.ctl.mem_fcn
    io.dmem.req.bits.typ := io.ctl.msk_sel
    io.dmem.req.bits.addr := io.exe.addr
    io.dmem.req.bits.data := io.exe.data
    //io.stall := !io.imem.resp.valid || !((dmem_val && io.dmem.resp.valid) || !dmem_val)
    io.wb.data :=  io.dmem.resp.bits.data
    io.stall := io.ctl.mem_en && !io.dmem.resp.valid
    val valid = RegNext(io.dmem.resp.valid)
    io.ls_valid := valid
}