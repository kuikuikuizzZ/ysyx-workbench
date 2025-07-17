
package npc

import chisel3._

import npc.common.{ysyx_24100012_Config, 
                     ysyx_24100012_AXI4LiteMaster,ysyx_24100012_AXI4LiteSlave,
                     ysyx_24100012_AXI4LiteArbiter}
import npc._
import npc.devices._

class ysyxSoCFull extends Module 
{
    val io = IO(new Bundle{
        val halt = Output(Bool())
    })
   implicit val conf = ysyx_24100012_Config()

    val core = Module(new ysyx_24100012())
    core.io := DontCare

    // val axi_dmem_slave = Module(new ysyx_24100012_AXI4LiteSlave())
    // val axi_imem_slave = Module(new ysyx_24100012_AXI4LiteSlave())
    // val axi_dmem       = Module(new ysyx_24100012_AXI4LiteMemRandomDelay())
    // val axi_imem       = Module(new ysyx_24100012_AXI4LiteMemRandomDelay())

    // axi_dmem_slave.io := DontCare
    // axi_imem_slave.io := DontCare
    // axi_imem.io.clock := clock
    // axi_imem.io.reset := reset
    // axi_dmem.io.clock := clock
    // axi_dmem.io.reset := reset

    // core.io.dmem_axi <>  axi_dmem_slave.io.axi_io
    // core.io.imem_axi <>  axi_imem_slave.io.axi_io
    
    // axi_dmem_slave.io.out.dr <> axi_dmem.io.dr
    // axi_dmem_slave.io.out.dw <> axi_dmem.io.dw
    // axi_imem_slave.io.out.dr <> axi_imem.io.dr
    // axi_imem_slave.io.out.dw <> axi_imem.io.dw

    // val axi_arbiter     = Module(new AXI4LiteArbiter(numMasters=2))
    // val axi_mem_slave   = Module(new AXI4LiteSlave())
    // val axi_mem        = Module(new ysyx_24100012_AXI4LiteMemRandomDelay())

    // axi_mem_slave.io := DontCare
    // axi_mem.io.clock := clock
    // axi_mem.io.reset := reset

    // axi_arbiter.io.slave <> axi_mem_slave.io.axi_io
    // axi_mem_slave.io.out.dr <> axi_mem.io.dr
    // axi_mem_slave.io.out.dw <> axi_mem.io.dw
    // core.io.dmem_axi <>  axi_arbiter.io.masters(0)
    // core.io.imem_axi <>  axi_arbiter.io.masters(1)

    val axi_mem_slave   = Module(new ysyx_24100012_AXI4LiteSlave())
    val axi_mem        = Module(new ysyx_24100012_AXI4LiteMemRandomDelay())

    axi_mem_slave.io := DontCare
    axi_mem.io.clock := clock
    axi_mem.io.reset := reset

    axi_mem_slave.io.out.dr <> axi_mem.io.dr
    axi_mem_slave.io.out.dw <> axi_mem.io.dw
    core.io.master <>  axi_mem_slave.io.axi_io
    io.halt := core.io.halt
}
