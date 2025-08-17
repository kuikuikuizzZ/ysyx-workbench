package npc.common
import chisel3._
import chisel3.util._

trait PrivilegedConstants
{
   val MTVEC = 0x100
   val START_ADDR = (0x30000000.U(32.W))
   // val START_ADDR = "h8000_0000".U
   val SDRAM_BASE = (0xa0000000.U)(39.W)
   val SDRAM_SIZE = (0x10000000.U)(39.W)
   val FLASH_BASE = (0x30000000.U)(39.W)
   val FLASH_SIZE = (0x01000000.U)(39.W)
   val PSRAM_BASE = (0x80000000.U)(39.W)
   val PSRAM_SIZE = (0x01000000.U)(39.W)
   val CLINT_BASE = (0x02000000.U)(39.W)
   val CLINT_SIZE = 0xC000.U

   val MSTATUS = 0x1800.U
   

   val SZ_PRV = 2
   val PRV_U = 0
   val PRV_S = 1
   val PRV_M = 3
}

