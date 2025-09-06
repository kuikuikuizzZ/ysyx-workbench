import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.formal._
import org.scalatest.flatspec.AnyFlatSpec

import npc.common._
import npc._

class CacheTest extends Module {
  val io = IO(new Bundle {
    val req = new ICacheIO
    val block = Input(Bool())
  })

  val memSize = 128  // byte
  val mem = Mem(memSize / 4, UInt(32.W))
  val dut = Module(new ysyx_24100012_ICache)

  dut.io.req <> io.req

  val dutData = dut.io.inst
  val refRData = mem(io.req.pc)
  when (dut.io.resp.valid) {
    assert(dutData === refData)
  }
}

class FormalTest extends AnyFlatSpec with ChiselScalatestTester with Formal {
  "Test" should "pass" in {
    verify(new CacheTest, Seq(BoundedCheck(3), BtormcEngineAnnotation))
  }
}