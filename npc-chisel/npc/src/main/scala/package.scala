package npc
import npc.constants._

import chisel3._
import chisel3.util._

object Constants extends
   ScalarOpConstants with
   npc.common.MemoryOpConstants with
   NPCProcConstants with
   PrivilegedConstants with
   RISCVConstants 
{
}