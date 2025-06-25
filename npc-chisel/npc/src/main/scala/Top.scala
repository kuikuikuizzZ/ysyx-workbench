
package npc

import chisel3._

import npc.common.{YSYX24100012Config, AsyncScratchPadMemory, SyncScratchPadMemory}
import npc._

class Top extends Module 
{
    val io = IO(new Bundle{
        val halt = Output(Bool())
    })
   implicit val conf = YSYX24100012Config()

    val core = Module(new Core())
    core.io := DontCare

    val memory = Module(new AsyncScratchPadMemory(num_core_ports = 2))
    core.io.dmem <> memory.io.core_ports(0)
    core.io.imem <> memory.io.core_ports(1)
    io.halt := core.io.halt
}
