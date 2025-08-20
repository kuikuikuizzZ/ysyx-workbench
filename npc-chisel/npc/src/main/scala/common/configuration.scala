package npc
package common


case class ysyx_24100012_Config(
 xprlen: Int = 32) {
    val xlen = xprlen
    val idBits = 4
    val sizeBits = 3
    val lenBits = 8
    val burstBits = 2
    val maskBits = xlen/8
    val perfCountBits = 32

    val ICacheSizeBits = 4
    val ICacheBlockBits = 1
}