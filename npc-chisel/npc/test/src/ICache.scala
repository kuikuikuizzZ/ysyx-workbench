// package npc

// import chisel3._
// import chisel3.util._
// import chisel3.simulator.EphemeralSimulator._
// import org.scalatest.flatspec.AnyFlatSpec
// import org.scalatest.matchers.must.Matchers

// import npc._
// import npc.common._
// import javax.xml.crypto.Data

// class ICacheImplTest extends AnyFlatSpec with Matchers {
//   implicit val conf = new Config()
//   implicit val config = new TestConfig
//   import AddressUtils._

//   // "ICacheImpl" should "handle cache hit correctly" in {
//   //   simulate(new ICacheImpl) { dut =>
//   //     println("=== 测试1: 缓存命中场景 ===")
      
//   //     val testAddr = 0x12345678L.U
//   //     val testData = 123456789L
//   //     val testIdx = getIdx(testAddr)
//   //     val testTag = getTag(testAddr)
      
//   //     // 初始化信号
//   //     dut.io.req.valid.poke(false.B)
//   //     dut.io.lsu_s1_kill.poke(false.B)
//   //     dut.clock.step(dut.nLines)
      
//   //     // 阶段0: 发送读取请求
//   //     dut.io.req.valid.poke(true.B)
//   //     dut.io.req.bits.addr.poke(testAddr)
//   //     dut.io.req.bits.rw.poke(false.B) // 读操作
//   //     dut.io.debug.load_debug.s0_valid.expect(true.B)
      
//   //     // 模拟metaRead就绪
//   //     dut.clock.step(1)
//   //     dut.io.metaRead.req.valid.expect(true.B)
//   //     dut.io.metaRead.req.bits.index.expect(testIdx)
//   //     dut.clock.step(1)
//   //     println(monitorPipelineState(dut,1))
//   //     // 阶段1: 提供meta响应，模拟命中
//   //     dut.io.req.valid.poke(false.B) // 请求只持续一拍
//   //     dut.io.metaRead.resp.valid.poke(true.B)
//   //     for (i <- 0 until dut.nWays) {
//   //       dut.io.metaRead.resp.bits.data(i).tag.poke(testTag)
//   //       dut.io.metaRead.resp.bits.data(i).valid.poke((i == 0).B)
//   //     }
//   //     dut.io.debug.s1_valid.expect(true.B)
//   //     dut.io.debug.s1_fire.expect(true.B)
//   //     dut.io.dataRead.req.valid.expect(true.B)
//   //     // 设置所有way的meta，但只有way0命中
//   //     dut.clock.step(1)
//   //     // for (i <- 0 until dut.nWays) {
//   //     //   // dut.io.debug.s1_hit_vec(i).expect((i == 0).B)
//   //     //   println(s"Way $i hit: " + dut.io.debug.s1_hit_vec(i).peek().litToBoolean)
//   //     //   println("Way $i valid " +  dut.io.debug.s1_meta(i).valid.peek().litToBoolean + " tag: " + dut.io.debug.s1_meta(i).tag.peek().litValue + " testTag " + testTag)
//   //     //   println("s1_tag_match_ways: " + dut.io.debug.s1_tag_match_way(i).peek().litValue)
//   //     // }
//   //     dut.io.debug.s2_valid.expect(true.B)
//   //     dut.io.dataRead.req.bits.index.expect(testIdx)
//   //     println(monitorPipelineState(dut, 2))
      
//   //     // 阶段2: 提供data响应并检查结果
//   //     dut.io.metaRead.resp.valid.poke(false.B)
//   //     dut.io.dataRead.resp.valid.poke(true.B)
      
//   //     // 设置数据，way0有测试数据，其他为0
//   //     val responseData = Seq.tabulate(dut.nWays)(i => 
//   //       if (i == 0) testData.U else 0.U
//   //     )
//   //     for (i <- 0 until dut.nWays) {
//   //       dut.io.dataRead.resp.bits.data(i).data.poke(responseData(i))
//   //     }

      
//   //     dut.clock.step(1)
//   //     println(monitorPipelineState(dut, 3))
      
//   //     // 验证响应
//   //     dut.io.resp.valid.expect(true.B)
//   //     dut.io.resp.bits.miss.expect(false.B)
//   //     for (i <- 0 until dut.blockRows) {
//   //       if (i == 0) {
//   //         dut.io.resp.bits.data(i).expect(testData.U)
//   //       } else {
//   //         dut.io.resp.bits.data(i).expect(0.U)
//   //       }
//   //     }
      
//   //     println("缓存命中测试通过 ✓")
//   //   }
//   // }

//   it should "ICacheImpl handle cache miss correctly" in {
//     simulate(new ICacheImpl) { dut =>
//       println("=== 测试2: 缓存未命中场景 ===")
      
//       val testAddr = 0x12345678L
//       val testIdx = getIdx(testAddr.U)
//       val wrongTag = getTag(0x22345678L.U) // 错误标签导致未命中
//       val testData = 0x12345678L
//       // 初始化
//       dut.io.req.valid.poke(false.B)
//       dut.io.lsu_s1_kill.poke(false.B)
//       dut.io.metaRead.resp.valid.poke(false.B)
//       dut.io.reset.poke(true.B)
//       dut.clock.step(1)
//       dut.io.reset.poke(false.B)
//       dut.clock.step(dut.nLines)
//       dut.clock.step(1)
//       // 发送请求
//       dut.io.req.valid.poke(true.B)
//       dut.io.req.bits.addr.poke(testAddr.U)
//       dut.io.req.bits.rw.poke(false.B)
//       dut.io.metaRead.req.ready.poke(true.B)
      
//       dut.clock.step(1)
      
//       // 提供meta响应，所有way都不匹配
//       dut.io.req.valid.poke(false.B)
//       dut.io.debug.load_debug.s1_valid.expect(true.B)
//       dut.clock.step(1)
//       println(monitorPipelineState(dut, 2))
      
//       // 检查未命中信号和missBus请求
//       dut.io.debug.load_debug.s2_valid.expect(true.B)

//       dut.clock.step(1)
//       dut.io.missBus.resp.valid.poke(true.B)
//       for (i <- 0 until dut.blockRows) {
//         if (i == 0) {
//           dut.io.missBus.resp.bits.data(i).poke(testData.U)
//         } else {
//           dut.io.missBus.resp.bits.data(i).poke(0.U)
//         }
//       }

//       dut.io.resp.valid.expect(true.B)
//       dut.io.resp.bits.miss.expect(true.B)
//       for (i <- 0 until dut.blockRows) {
//         if (i == 0) {
//           dut.io.resp.bits.data(i).expect(testData.U)
//         } else {
//           dut.io.resp.bits.data(i).expect(0.U)
//         }
//       }
//       println("缓存未命中测试通过 ✓")
//     }
//   }

// }