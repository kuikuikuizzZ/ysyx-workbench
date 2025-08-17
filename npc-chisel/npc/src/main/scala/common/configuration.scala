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

    val ICacheSize = 16
    // tag bits = 32-4-2 = 26 (16 = 2^4,4 = 2^2 bytes)
    // valid bits = 1, tag bits = 26, 32 (4bytes) 
    // 1+ 26 +32 = 59
    val ICacheBlockBits = 59
}