
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
        val dmem_axi = new AXI4LiteIo()
        val exe = Flipped(new exeToLSUIo())
        val pc_io = Flipped(new PCOut())
        val wb = new LsuToWBIo()
        val ls_valid = Output(Bool())
    })
    io := DontCare
    val dmem = Module(new AXI4LiteMemeory())
    dmem.io.axi_port <> io.dmem_axi
    dmem.io.port.req.valid    := io.ctl.mem_en
    dmem.io.port.req.bits.fcn := io.ctl.mem_fcn
    dmem.io.port.req.bits.typ := io.ctl.msk_sel
    dmem.io.port.req.bits.addr := io.exe.addr
    dmem.io.port.req.bits.data := io.exe.data
    //io.stall := !io.imem.resp.valid || !((dmem_val && io.dmem.resp.valid) || !dmem_val)
    io.wb.data :=  dmem.io.port.resp.bits.data
    val valid = RegNext(dmem.io.port.resp.valid)
    io.ls_valid := valid
}