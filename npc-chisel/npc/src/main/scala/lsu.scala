
package npc

import chisel3._
import chisel3.util._

import npc.common._
import npc.Constants._
import javax.xml.transform.OutputKeys

class LsuToWBIo(implicit val conf: ysyx_24100012_Config) extends Bundle {
    val data = Output(UInt(conf.xprlen.W))
}
class LSUDebugPort(implicit val conf: ysyx_24100012_Config) extends Bundle {
    val mem_en      = Output(Bool())
    val fcn         = Output(Bool())
    val addr        = Output(UInt(conf.xprlen.W))
    val rdata       = Output(UInt(conf.xprlen.W))
    val wdata       = Output(UInt(conf.xprlen.W))
    val valid       = Output(Bool())
    val typ        = Output(UInt(2.W))
}

class ysyx_24100012_LSU(implicit val conf: ysyx_24100012_Config) extends Module {
    val io = IO(new Bundle {
        val ctl = Flipped(new CtlToLSUIo())
        val port = new MemPortIo(conf.xprlen)
        val exe = Flipped(new exeToLSUIo())
        val pc_io = Flipped(new PCOut())
        val wb = new LsuToWBIo()
        val ls_valid = Output(Bool())
        val debug = new LSUDebugPort
        val clintIO = Flipped(new Bundle{
            val dr      =   new AXIRport(conf.xprlen, conf.xlen)
            val dw      =   new AXIWport(conf.xprlen, conf.xlen)
        })
    })
    io := DontCare
    val valid = WireInit(false.B)

    when (io.ctl.mem_en && io.exe.addr >= CLINT_BASE && io.exe.addr < (CLINT_BASE + CLINT_SIZE)){
        io.port.req.valid    := false.B
        when (io.ctl.mem_fcn === M_XRD){
            io.clintIO.dr.en := true.B
            io.clintIO.dr.addr := io.exe.addr
            io.wb.data := io.clintIO.dr.data
            valid := io.clintIO.dr.ready
        } .otherwise{
            io.clintIO.dr.en := false.B
        }
    } .otherwise {
        io.port.req.valid    := io.ctl.mem_en
        io.port.req.bits.fcn := io.ctl.mem_fcn
        io.port.req.bits.typ := io.ctl.msk_sel
        io.port.req.bits.addr := io.exe.addr
        io.port.req.bits.data := io.exe.data
        //io.stall := !io.imem.resp.valid || !((dmem_val && io.dmem.resp.valid) || !dmem_val)
        io.wb.data :=  io.port.resp.bits.data
        valid := io.port.resp.valid
    }
    
    io.ls_valid := valid

    /* Debug */
    io.debug.mem_en     := io.ctl.mem_en
    io.debug.addr       := io.exe.addr
    io.debug.fcn        := io.ctl.mem_fcn
    io.debug.wdata      := io.exe.data
    io.debug.rdata      := io.port.resp.bits.data
    io.debug.valid      := io.port.resp.valid
    io.debug.typ        := io.ctl.mem_typ
}