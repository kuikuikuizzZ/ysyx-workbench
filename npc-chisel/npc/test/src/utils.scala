
package npc

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import npc._
import npc.common._
import javax.xml.crypto.Data

class TestConfig (implicit val conf:Config) extends CacheConfig {
  val xlen = 32
}

// 地址分解辅助函数
object AddressUtils {
  def getTag(addr: UInt)(implicit config: TestConfig): UInt = {
    addr(config.xlen - 1, config.idxBits + config.blockIdx+config.byteOffsetBits)
  }
  
  def getIdx(addr: UInt)(implicit config: TestConfig): UInt = {
    addr(config.idxBits + config.blockIdx + config.byteOffsetBits - 1, config.blockIdx+config.byteOffsetBits)
  }
  
  def getOffset(addr: UInt)(implicit config: TestConfig): UInt = {
    addr(config.blockIdx + config.byteOffsetBits -1, config.byteOffsetBits)
  }
}