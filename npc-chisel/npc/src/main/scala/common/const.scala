package npc.common

trait PrivilegedConstants
{
   val MTVEC = 0x100
   val START_ADDR = 0x80000000

   val SZ_PRV = 2
   val PRV_U = 0
   val PRV_S = 1
   val PRV_M = 3
}
