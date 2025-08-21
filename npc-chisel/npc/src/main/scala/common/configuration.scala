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
    val burstLength = 2.U(8.W)
    val AXIBurstLenBits = 8.U

    val ICacheSizeBits = 2
    val ICacheBlockBits = 2
    val ICacheEnableBurst = false.B
}