
package npc

import chisel3._

import npc.common.{Config, 
                     AXI4LiteMaster,TopAXI4LiteSlave,
                     TopAXI4Slave,AXI4LiteArbiter}
import npc._
import npc.devices.{TopAXI4LiteMem,IverilogAXI4LiteMem}

class IverilogTop extends Module { 
    val io = IO(new Bundle{
        // val halt = Output(Bool())
    })
   implicit val conf = Config()
    io := DontCare
    val core = Module(new ysyx_24100012())
    core.io := DontCare


    val axi_mem_slave   = Module(new TopAXI4Slave())
    val axi_mem        = Module(new IverilogAXI4LiteMem())

    axi_mem_slave.io := DontCare
    axi_mem.io.clock := clock
    axi_mem.io.reset := reset

    axi_mem_slave.io.out.dr <> axi_mem.io.dr
    axi_mem_slave.io.out.dw <> axi_mem.io.dw
    core.io.master <>  axi_mem_slave.io.axi_io
}

class Top extends Module 
{
    val io = IO(new Bundle{
        // val halt = Output(Bool())
    })
   implicit val conf = Config()
    io := DontCare
    val core = Module(new ysyx_24100012())
    core.io := DontCare


    val axi_mem_slave   = Module(new TopAXI4Slave())
    val axi_mem        = Module(new TopAXI4LiteMem())

    axi_mem_slave.io := DontCare
    axi_mem.io.clock := clock
    axi_mem.io.reset := reset

    axi_mem_slave.io.out.dr <> axi_mem.io.dr
    axi_mem_slave.io.out.dw <> axi_mem.io.dw
    core.io.master <>  axi_mem_slave.io.axi_io
    // io.halt := core.io.halt
}

class SRAMTop extends Module { 
    val io = IO(new Bundle{
        // val halt = Output(Bool())
        val addr = Input(UInt(32.W))
        val data = Output(UInt(32.W))
        val write = Input(Bool())
    })
   implicit val conf = Config()
    io := DontCare

    val cache = Module(new CacheSRAMTemplate(new DataBundle, 8, 4))
    cache.io := DontCare
    when(io.write){
        cache.io.w.req.valid := true.B
        cache.io.w.req.bits.index := io.addr(2,0)
        cache.io.w.req.bits.waymask := 1.U
        cache.io.w.req.bits.data.data := io.addr
    } .otherwise{
        cache.io.r.req.valid := true.B
        cache.io.r.req.bits.index := io.addr(2,0)
    }
    val data = cache.io.r.resp.bits.data.map(_.asTypeOf(new DataBundle).data)
    io.data := data(0)
}

