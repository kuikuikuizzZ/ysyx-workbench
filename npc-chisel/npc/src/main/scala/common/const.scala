package npc.common
import chisel3._
import chisel3.util._

trait PrivilegedConstants
{
   val MTVEC = 0x100
   val START_ADDR = (0x30000000.U(32.W))
   // val START_ADDR = "h8000_0000".U
   val SDRAM_BASE = (0xa0000000L.U(32.W))
   val SDRAM_SIZE = (0x10000000L.U(32.W))
   val FLASH_BASE = (0x30000000L.U(32.W))
   val FLASH_SIZE = (0x01000000L.U(32.W))
   val PSRAM_BASE = (0x80000000L.U(32.W))
   val PSRAM_SIZE = (0x01000000L.U(32.W))

   val MSTATUS = 0x1800.U
   
   val CLINT_BASE = (0x02000000.U(32.W))
   val CLINT_SIZE = 0xC000.U

   val SZ_PRV = 2
   val PRV_U = 0
   val PRV_S = 1
   val PRV_M = 3
}

trait ExceptionCodesConstants {
  // 异常号定义 (符合 RISC-V 特权规范)
  val EXC_NORMAL                  = 0.U(5.W)
  val EXC_INSTR_ADDR_MISALIGNED   = 0x10.U(5.W)
  val EXC_INSTR_ACCESS_FAULT      = 0x11.U(5.W)
  val EXC_ILLEGAL_INSTR           = 0x12.U(5.W)
  val EXC_BREAKPOINT              = 0x13.U(5.W)
  val EXC_LOAD_ADDR_MISALIGNED    = 0x14.U(5.W)
  val EXC_LOAD_ACCESS_FAULT       = 0x15.U(5.W)
  val EXC_STORE_ADDR_MISALIGNED   = 0x16.U(5.W)
  val EXC_STORE_ACCESS_FAULT      = 0x17.U(5.W)
  val EXC_ECALL_U_MODE            = 0x18.U(5.W)
  val EXC_ECALL_S_MODE            = 0x19.U(5.W)
  val EXC_ECALL_M_MODE            = 0x1b.U(5.W)
  val EXC_INSTR_PAGE_FAULT        = 0x1c.U(5.W)
  val EXC_LOAD_PAGE_FAULT         = 0x1d.U(5.W)
  val EXC_STORE_PAGE_FAULT        = 0x1f.U(5.W)
}