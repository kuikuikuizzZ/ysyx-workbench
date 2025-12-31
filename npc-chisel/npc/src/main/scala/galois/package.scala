package npc.galois
import npc.common._
import npc._

import chisel3._
import chisel3.util._

object Constants extends
   npc.common.ScalarOpConstants with
   npc.common.MemoryOpConstants with
   npc.common.NPCProcConstants with
   npc.common.PrivilegedConstants with
   npc.common.RISCVConstants with
   npc.common.AXI4BurstTypes with
   npc.common.ExceptionCodesConstants
{
}