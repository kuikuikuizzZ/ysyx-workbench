package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import npc._
import npc.common._


class CacheSRAMTemplateTest extends AnyFlatSpec with Matchers {
    implicit val conf = Config()
    "CacheSRAMTemplate" should "initialize correctly after reset" in {
        simulate(new CacheSRAMTemplate(UInt(32.W), line = 8, ways = 4)) { dut =>
            // 1. 检查复位状态
            dut.reset.poke(true.B)
            dut.clock.step(5) // 经过几个时钟周期
            dut.reset.poke(false.B)
            // 2. 检查复位完成信号
            dut.clock.step(3) // 经过几个时钟周期
            // 复位完成后，读写请求应该就绪
            dut.io.r.req.ready.expect(false.B)
            dut.io.w.req.ready.expect(false.B)
            dut.clock.step(5) // 经过几个时钟周期
            // 响应在初始状态下应为无效
            dut.io.r.req.ready.expect(true.B)
            dut.io.w.req.ready.expect(true.B)
            dut.io.r.resp.valid.expect(false.B)
        }
    }

    it should "perform whole line write and read operations correctly" in {
    simulate(new CacheSRAMTemplate(UInt(32.W), line = 8, ways = 4)) { dut =>
        // 等待复位完成
        // while (dut.resetState.peek().litToBoolean) {
        //     dut.clock.step(1)
        // }

        val testIndex = 4
        val testData = Seq(11111111L, 22222222L, 33333333L, 44444444L)
        val waymask = "b1111".U // 选择所有way

        // 执行写操作
        dut.io.w.req.valid.poke(true.B)
        dut.io.w.req.bits.index.poke(testIndex.U)
        for(i <- 0 until 4){
            val mask = waymask.litValue.toInt & (1 << i)
            dut.io.w.req.bits.waymask.poke(mask)
            dut.io.w.req.bits.data.poke(testData(i).U)
            dut.clock.step(1)
        }
        dut.clock.step(1)
        dut.io.w.req.valid.poke(false.B)

        // 执行读操作
        dut.io.r.req.valid.poke(true.B)
        dut.io.r.req.bits.index.poke(testIndex.U)
        dut.clock.step(1) // 进入读状态
        dut.io.r.req.valid.poke(false.B)

        // 验证读响应（注意：SyncReadMem有一拍延迟）
        // dut.clock.step(1) // 等待响应有效
        dut.io.r.resp.valid.expect(true.B)
        // 验证所有被选中的way都返回了正确数据
        for (i <- 0 until 4) {
            dut.io.r.resp.bits.data(i).expect(testData(i).U)
        }
    }
  }

  it should "perform basic write and read operations correctly" in {
    simulate(new CacheSRAMTemplate(UInt(32.W), line = 8, ways = 4)) { dut =>
        // 等待复位完成
        // while (dut.resetState.peek().litToBoolean) {
        //     dut.clock.step(1)
        // }

        val testIndex = 4
        val testData = 11111111L
        val waymask = "b0001".U // 选择所有way

        // 执行写操作
        dut.io.w.req.valid.poke(true.B)
        dut.io.w.req.bits.index.poke(testIndex.U)
        dut.io.w.req.bits.waymask.poke(waymask)
        dut.io.w.req.bits.data.poke(testData.U)
        dut.clock.step(1)
        dut.io.w.req.valid.poke(false.B)

        // 执行读操作
        dut.io.r.req.valid.poke(true.B)
        dut.io.r.req.bits.index.poke(testIndex.U)
        dut.clock.step(1) // 进入读状态
        dut.io.r.req.valid.poke(false.B)

        // 验证读响应（注意：SyncReadMem有一拍延迟）
        // dut.clock.step(1) // 等待响应有效
        dut.io.r.resp.valid.expect(true.B)
        // 验证所有被选中的way都返回了正确数据
        for (i <- 0 until 4) {
            if ((waymask.litValue.toInt & (1 << i)) != 0){
                dut.io.r.resp.bits.data(i).expect(testData.U)
            }
        }
    }
  }

    it should "perform basic write and read metaBundle correctly" in {
    simulate(new CacheSRAMTemplate(new MetaBundle, line = 8, ways = 4)) { dut =>
        // 等待复位完成
        // while (dut.resetState.peek().litToBoolean) {
        //     dut.clock.step(1)
        // }
        import npc._
        val testIndex = 4
        val waymask = "b0001".U // 选择所有way
        val testTag = 0x1234.U
        // 执行写操作
        dut.io.w.req.valid.poke(true.B)
        dut.io.w.req.bits.index.poke(testIndex.U)
        dut.io.w.req.bits.waymask.poke(waymask)
        dut.io.w.req.bits.data.tag.poke(testTag)
        dut.io.w.req.bits.data.valid.poke(true.B)
        dut.clock.step(1)
        dut.io.w.req.valid.poke(false.B)

        // 执行读操作
        dut.io.r.req.valid.poke(true.B)
        dut.io.r.req.bits.index.poke(testIndex.U)
        dut.clock.step(1) // 进入读状态
        dut.io.r.req.valid.poke(false.B)

        // 验证读响应（注意：SyncReadMem有一拍延迟）
        // dut.clock.step(1) // 等待响应有效
        dut.io.r.resp.valid.expect(true.B)
        // 验证所有被选中的way都返回了正确数据
        for (i <- 0 until 4) {
            if ((waymask.litValue.toInt & (1 << i)) != 0){
                dut.io.r.resp.bits.data(i).tag.expect(testTag)
                dut.io.r.resp.bits.data(i).valid.expect(true.B)
            } else{
                dut.io.r.resp.bits.data(i).valid.expect(false.B)
            }
        }
    }
  }


  it should "can not handle concurrent read and write operations correctly" in {
    simulate(new CacheSRAMTemplate(UInt(32.W), line = 64, ways = 4)) { dut =>

      val readIndex = 5
      val writeIndex = 10
      val writeData = 0xDEADBEEFL

      // 同时发起读和写请求（不同地址）
      dut.io.r.req.valid.poke(true.B)
      dut.io.r.req.bits.index.poke(readIndex.U)
      dut.io.w.req.ready.expect(false.B)

      dut.clock.step(1)

      // 验证写请求被接受

      // 验证读响应
      dut.clock.step(1) // 等待读响应
      dut.io.r.resp.valid.expect(true.B)
    }
  }

//   it should "handle edge cases and boundary addresses" in {
//     test(new CacheSRAMTemplate(UInt(32.W), line = 64, ways = 4)) { dut =>
//       // 等待复位完成
//       while (dut.resetState.peek().litToBoolean) {
//         dut.clock.step(1)
//       }

//       // 测试边界地址：0 和 最大值
//       val edgeCases = Seq(0, 63)

//       for ((addr, i) <- edgeCases.zipWithIndex) {
//         val testData = 0x1000 + i

//         // 写入边界地址
//         dut.io.w.req.valid.poke(true.B)
//         dut.io.w.req.bits.index.poke(addr.U)
//         dut.io.w.req.bits.waymask.poke("b1111".U)
//         dut.io.w.req.bits.data.poke(testData.U)
//         dut.clock.step(1)
//         dut.io.w.req.valid.poke(false.B)

//         // 从边界地址读取
//         dut.io.r.req.valid.poke(true.B)
//         dut.io.r.req.bits.index.poke(addr.U)
//         dut.clock.step(2) // 等待读响应

//         dut.io.r.resp.valid.expect(true.B)
//         for (way <- 0 until 4) {
//           dut.io.r.resp.bits.data(way).expect(testData.U)
//         }
//       }
//     }
//   }

//   it should "pass random stress test" in {
//     test(new CacheSRAMTemplate(UInt(32.W), line = 64, ways = 4)) { dut =>
//       // 等待复位完成
//       while (dut.resetState.peek().litToBoolean) {
//         dut.clock.step(1)
//       }

//       val rnd = new Random(42) // 固定随机种子以便复现
//       val numOperations = 100
//       val memoryModel = scala.collection.mutable.Map[Int, Long]()

//       // 随机操作测试
//       for (i <- 0 until numOperations) {
//         val isWrite = rnd.nextBoolean()
//         val addr = rnd.nextInt(64) // 0-63
//         val waymask = rnd.nextInt(15) + 1 // 1-15 (至少选一个way)
//         val data = rnd.nextLong() & 0xFFFFFFFFL

//         if (isWrite) {
//           // 执行写操作
//           dut.io.w.req.valid.poke(true.B)
//           dut.io.w.req.bits.index.poke(addr.U)
//           dut.io.w.req.bits.waymask.poke(waymask.U)
//           dut.io.w.req.bits.data.poke(data.U)
//           dut.clock.step(1)
//           dut.io.w.req.valid.poke(false.B)

//           // 更新内存模型
//           memoryModel(addr) = data
//         } else {
//           // 执行读操作
//           dut.io.r.req.valid.poke(true.B)
//           dut.io.r.req.bits.index.poke(addr.U)
//           dut.clock.step(1)
//           dut.io.r.req.valid.poke(false.B)

//           // 验证读响应
//           dut.clock.step(1)
//           if (memoryModel.contains(addr)) {
//             val expected = memoryModel(addr)
//             for (way <- 0 until 4) {
//               if ((waymask & (1 << way)) != 0) {
//                 dut.io.r.resp.bits.data(way).expect(expected.U)
//               }
//             }
//           }
//         }

//         // 偶尔插入空闲周期
//         if (i % 10 == 0) {
//           dut.io.r.req.valid.poke(false.B)
//           dut.io.w.req.valid.poke(false.B)
//           dut.clock.step(1)
//         }
//       }
//     }
//   }
}