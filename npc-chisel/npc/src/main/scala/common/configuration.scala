package npc
package common

import chisel3._
import chisel3.util._

case class ysyx_24100012_Config(
 xprlen: Int = 32) {
    val xlen = xprlen
    val idBits = 4
    val sizeBits = 3
    val lenBits = 8
    val burstBits = 2
    val maskBits = xlen/8
    val perfCountBits = 32
    val AXIBurstLenBits = 8
    val burstLength = 0.U   // burstLength = axlen - 1   

    val ICacheSizeBits = 3
    val ICacheBlockBits = 1
    val ICacheEnableBurst = false.B
}