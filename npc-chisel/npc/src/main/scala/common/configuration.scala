package npc
package common

import chisel3._
import chisel3.util._

case class Config(
 xprlen: Int = 32) {
    val xlen = xprlen
    val idBits = 4
    val sizeBits = 3
    val lenBits = 8
    val burstBits = 2
    val maskBits = xlen/8
    val perfCountBits = 32
    val AXIBurstLenBits = 8
    val burstLength = 1.U   // burstLength = axlen - 1   

    val ICacheSizeBits = sys.env.get("ICacheSizeBits").map(_.toInt).getOrElse(2)
    val ICacheBlockBits = sys.env.get("ICacheBlockBits").map(_.toInt).getOrElse(1)
    val EnableBurst = sys.env.get("ICacheEnableBurst").map(java.lang.Boolean.parseBoolean).getOrElse(true)
    val ICacheEnableBurst = if (EnableBurst) true.B else false.B

    val USE_FULL_BYPASSING = true
    val ENABLE_DEBUG = true
}