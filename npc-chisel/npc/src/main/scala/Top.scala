
package npc

import chisel3._

import npc.common.{YSYX24100012Config, AsyncScratchPadMemory, SyncScratchPadMemory,AsyncMemory,SyncMemory}
import npc._

class Top extends Module 
{
    val io = IO(new Bundle{
        val halt = Output(Bool())
    })
   implicit val conf = YSYX24100012Config()

    val core = Module(new Core())
    core.io := DontCare

    val imemory = Module(new SyncMemory())
    val dmemory = Module(new AsyncMemory())
    core.io.dmem <> dmemory.io.port
    core.io.imem <> imemory.io.port
    io.halt := core.io.halt
}
