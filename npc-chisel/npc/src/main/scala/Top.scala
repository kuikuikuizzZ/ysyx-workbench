
package npc

import chisel3._

import npc.common.{YSYX24100012Config, AsyncScratchPadMemory,
                     SyncScratchPadMemory,AsyncMemory,
                     SyncMemory, AXI4LiteMemeory,
                     AXI4LiteMaster,AXI4LiteSlave,
                     AXI4LiteArbiter}
import npc._

class Top extends Module 
{
    val io = IO(new Bundle{
        val halt = Output(Bool())
    })
   implicit val conf = YSYX24100012Config()

    val core = Module(new Core())
    core.io := DontCare

    val axi_dmem_slave = Module(new AXI4LiteSlave())
    val axi_imem_slave = Module(new AXI4LiteSlave())
    axi_dmem_slave.io := DontCare
    axi_imem_slave.io := DontCare
    core.io.dmem_axi <>  axi_dmem_slave.io.axi_io
    core.io.imem_axi <>  axi_imem_slave.io.axi_io


    // val axi_arbiter = Module(new AXI4LiteArbiter(numMasters=2))
    // val axi_mem_slave = Module(new AXI4LiteSlave())
    // axi_mem_slave.io := DontCare
    // axi_arbiter.io.slave <> axi_mem_slave.io.axi_io
    // core.io.dmem_axi <>  axi_arbiter.io.masters(0)
    // core.io.imem_axi <>  axi_arbiter.io.masters(1)





    io.halt := core.io.halt
}
