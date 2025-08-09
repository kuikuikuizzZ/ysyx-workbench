package npc.common
import chisel3._
import chisel3.util._

trait PrivilegedConstants
{
   val MTVEC = 0x100
   val START_ADDR = (0x30000000.U(32.W))
   // val START_ADDR = "h8000_0000".U

   val MSTATUS = 0x1800.U
   
   val CLINT_BASE = 0x02000000.U
   val CLINT_SIZE = 0xC000

   val SZ_PRV = 2
   val PRV_U = 0
   val PRV_S = 1
   val PRV_M = 3
}

