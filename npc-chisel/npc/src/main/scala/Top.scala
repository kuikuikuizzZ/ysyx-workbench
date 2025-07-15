
package npc

import chisel3._

import npc.common.{YSYX24100012Config, 
                     AXI4LiteMaster,AXI4LiteSlave,
                     AXI4LiteArbiter}
import npc._
import npc.devices._

class Top extends Module 
{
    val io = IO(new Bundle{
        val halt = Output(Bool())
    })
   implicit val conf = YSYX24100012Config()

    val core = Module(new Core())
    core.io := DontCare

    // val axi_dmem_slave = Module(new AXI4LiteSlave())
    // val axi_imem_slave = Module(new AXI4LiteSlave())
    // val axi_dmem       = Module(new YSYX2400012AXI4LiteMemRandomDelay())
    // val axi_imem       = Module(new YSYX2400012AXI4LiteMemRandomDelay())

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

    val axi_arbiter     = Module(new AXI4LiteArbiter(numMasters=2))
    val axi_mem_slave   = Module(new AXI4LiteSlave())
    val axi_mem        = Module(new YSYX2400012AXI4LiteMem())

    axi_mem_slave.io := DontCare
    axi_mem.io.clock := clock
    axi_mem.io.reset := reset

    axi_arbiter.io.slave <> axi_mem_slave.io.axi_io
    axi_mem_slave.io.out.dr <> axi_mem.io.dr
    axi_mem_slave.io.out.dw <> axi_mem.io.dw
    core.io.dmem_axi <>  axi_arbiter.io.masters(0)
    core.io.imem_axi <>  axi_arbiter.io.masters(1)

    io.halt := core.io.halt
}
