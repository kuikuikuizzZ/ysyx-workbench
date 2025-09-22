
package npc

import chisel3._

import npc.common.{Config, 
                     AXI4LiteMaster,AXI4LiteSlave,AXI4BurstSlave
                     AXI4LiteArbiter}
import npc._
import npc.devices.{ysyx_24100012_AXI4LiteMem}

class ysyxSoCFull extends Module 
{
    val io = IO(new Bundle{
        // val halt = Output(Bool())
    })
   implicit val conf = Config()
    io := DontCare
    val core = Module(new ysyx_24100012())
    core.io := DontCare


    val axi_mem_slave   = Module(new AXI4BurstSlave())
    val axi_mem        = Module(new ysyx_24100012_AXI4LiteMem())

    axi_mem_slave.io := DontCare
    axi_mem.io.clock := clock
    axi_mem.io.reset := reset

    axi_mem_slave.io.out.dr <> axi_mem.io.dr
    axi_mem_slave.io.out.dw <> axi_mem.io.dw
    core.io.master <>  axi_mem_slave.io.axi_io
    // io.halt := core.io.halt
}
