package npc.common
import chisel3._
import chisel3.util._

trait PrivilegedConstants
{
   val MTVEC = 0x100
   val START_ADDR = "h80000000".U
   val MSTATUS = 0x1800.U
   
   val SZ_PRV = 2
   val PRV_U = 0
   val PRV_S = 1
   val PRV_M = 3
}
