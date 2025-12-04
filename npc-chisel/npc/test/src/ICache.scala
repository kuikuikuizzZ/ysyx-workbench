package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import npc._
import npc.common._

class MissUnitTest extends AnyFlatSpec with Matchers {
  
  // 状态监控函数 - 适配MissUnit的状态机
  def monitorState(dut: MissUnit): String = {
    val state = dut.io.debug.state.peek().litValue.toInt
    
    val stateStr = state match {
      case 0 => "sRequesting"
      case 1 => "sReceiving" 
      case 2 => "sComplete"
      case _ => s"unknown($state)"
    }
    
    s"State: $stateStr"
  }

  // 基础配置
  // implicit val conf = new Config {
  //   override val xprlen = 32
  //   override val xlen = 32
  //   override val ICacheEnableBurst = false.B
  //   override val burstLength = 4
  //   override val maskBits = 4
  // }

  "MissUnit" should "handle single read operation correctly" in {
    implicit val conf = Config()      
    
    simulate(new MissUnit) { dut =>
      
      // 初始化
      dut.clock.step(3) // 复位周期
      println("=== 测试1: 单次读操作 ===")
      
      // 设置读请求
      val testAddr = 0x1000
      dut.io.bus.req.bits.addr.poke(testAddr.U)
      dut.io.bus.req.bits.vaddr.poke(testAddr.U)
      dut.io.bus.req.bits.waymask.poke("b1".U) // 选择way 0
      dut.io.bus.req.bits.store.poke(false.B)
      dut.io.axi_bus.req.ready.poke(true.B)
      dut.io.bus.req.valid.poke(true.B)
      
      // 验证AXI请求发出
      dut.clock.step(1)
      println(s"Cycle 1 - ${monitorState(dut)}")
      dut.io.debug.state.expect(1.U)
      // AXI从设备准备好接收地址
      dut.clock.step(2)
      println(s"Cycle 2 - ${monitorState(dut)}")
      
      // 模拟AXI响应数据
      val testData = 0x12345678L
      dut.io.axi_bus.resp.valid.poke(true.B)
      dut.io.axi_bus.resp.bits.data.poke(testData.U)
      dut.io.axi_bus.resp.bits.resp.poke(0.U) // OKAY
      dut.io.debug.state.expect(1.U)
  
      dut.clock.step(1)
      println(s"Cycle 3 - ${monitorState(dut)}")
      
      // 验证响应完成
      dut.io.bus.resp.valid.expect(true.B)
      dut.io.bus.resp.bits.data.expect(testData.U)
      dut.io.bus.resp.bits.resp.expect(0.U) // OKAY
      dut.io.bus.resp.bits.addr.expect(testAddr.U)
      dut.io.debug.state.expect(2.U)
      
      println("单次读操作测试通过 ✓")
    }
  }

  // it should "handle burst read operations when enabled" in {
  //   // 测试突发传输配置
  //   implicit val burstConf = new Config {
  //     override val xprlen = 32
  //     override val xlen = 32
  //     override val ICacheEnableBurst = true
  //     override val burstLength = 4
  //     override val maskBits = 4
  //   }
    
  //   simulate(new MissUnit) { dut =>
  //     println("=== 测试2: 突发读操作 ===")
      
  //     val testAddr = 0x2000
  //     dut.io.bus.req.bits.addr.poke(testAddr.U)
  //     dut.io.bus.req.bits.vaddr.poke(testAddr.U)
  //     dut.io.bus.req.bits.store.poke(false.B)
  //     dut.io.bus.req.valid.poke(true.B)
      
  //     // AXI准备接收
  //     dut.io.axi_bus.req.ready.poke(true.B)
  //     dut.clock.step(1)
  //     println(s"突发请求后 - ${monitorState(dut)}")
      
  //     // 验证突发请求参数
  //     dut.io.axi_bus.req.bits.burst.expect(1.U) // BURST_INCR
  //     dut.io.axi_bus.req.bits.burstlen.expect(burstConf.burstLength.U)
      
  //     // 模拟多个突发传输
  //     val testData = Seq(0x11111111L, 0x22222222L, 0x33333333L, 0x44444444L)
      
  //     for (i <- testData.indices) {
  //       dut.io.axi_bus.resp.valid.poke(true.B)
  //       dut.io.axi_bus.resp.bits.data.poke(testData(i).U)
  //       dut.io.axi_bus.resp.bits.last.poke(i == testData.length - 1)
        
  //       dut.clock.step(1)
  //       println(s"突发beat $i - ${monitorState(dut)}")
        
  //       // 检查缓存行构建
  //       if (i == testData.length - 1) {
  //         dut.io.bus.resp.valid.expect(true.B)
  //         // 这里应该验证完整的缓存行数据
  //       }
  //     }
      
  //     println("突发读操作测试通过 ✓")
  //   }
  // }

  // it should "handle store operations correctly" in {
  //   simulate(new MissUnit) { dut =>
  //     println("=== 测试3: 存储操作 ===")
      
  //     val testAddr = 0x3000
  //     val storeData = 0xDEADBEEFL
  //     val storeMask = 0xFL
      
  //     dut.io.bus.req.bits.addr.poke(testAddr.U)
  //     dut.io.bus.req.bits.store_data.poke(storeData.U)
  //     dut.io.bus.req.bits.store_wmask.poke(storeMask.U)
  //     dut.io.bus.req.bits.store.poke(true.B)
  //     dut.io.bus.req.valid.poke(true.B)
      
  //     dut.io.axi_bus.req.ready.poke(true.B)
  //     dut.clock.step(1)
      
  //     // 存储操作应该触发AXI写请求
  //     // 注意：当前MissUnit设计主要是读操作，存储可能需要额外逻辑
  //     println("存储操作基础测试完成")
  //   }
  // }

  // it should "handle bus errors gracefully" in {
  //   simulate(new MissUnit) { dut =>
  //     println("=== 测试4: 总线错误处理 ===")
      
  //     val testAddr = 0x4000
  //     dut.io.bus.req.bits.addr.poke(testAddr.U)
  //     dut.io.bus.req.bits.store.poke(false.B)
  //     dut.io.bus.req.valid.poke(true.B)
      
  //     dut.io.axi_bus.req.ready.poke(true.B)
  //     dut.clock.step(1)
      
  //     // 模拟错误响应
  //     dut.io.axi_bus.resp.valid.poke(true.B)
  //     dut.io.axi_bus.resp.bits.data.poke(0.U)
  //     dut.io.axi_bus.resp.bits.resp.poke(2.U) // SLVERR
  //     if (conf.ICacheEnableBurst) {
  //       dut.io.axi_bus.resp.bits.last.poke(true.B)
  //     }
      
  //     dut.clock.step(1)
      
  //     // 验证错误响应传递
  //     dut.io.bus.resp.valid.expect(true.B)
  //     dut.io.bus.resp.bits.resp.expect(2.U) // SLVERR
      
  //     println("总线错误处理测试通过 ✓")
  //   }
  // }

  // it should "handle back-to-back requests" in {
  //   simulate(new MissUnit) { dut =>
  //     println("=== 测试5: 背靠背请求 ===")
      
  //     // 第一个请求
  //     dut.io.bus.req.bits.addr.poke(0x5000.U)
  //     dut.io.bus.req.bits.store.poke(false.B)
  //     dut.io.bus.req.valid.poke(true.B)
  //     dut.io.axi_bus.req.ready.poke(true.B)
      
  //     dut.clock.step(1)
  //     dut.io.axi_bus.resp.valid.poke(true.B)
  //     dut.io.axi_bus.resp.bits.data.poke(0x55555555L.U)
  //     dut.io.axi_bus.resp.bits.resp.poke(0.U)
      
  //     dut.clock.step(1)
      
  //     // 立即发起第二个请求
  //     dut.io.bus.req.bits.addr.poke(0x6000.U)
  //     dut.io.bus.req.valid.poke(true.B)
      
  //     dut.clock.step(1)
  //     dut.io.axi_bus.resp.valid.poke(true.B)
  //     dut.io.axi_bus.resp.bits.data.poke(0x66666666L.U)
  //     dut.io.axi_bus.resp.bits.resp.poke(0.U)
      
  //     dut.clock.step(1)
      
  //     dut.io.bus.resp.valid.expect(true.B)
  //     println("背靠背请求测试通过 ✓")
  //   }
  // }

  // it should "handle pipeline stall scenarios" in {
  //   simulate(new MissUnit) { dut =>
  //     println("=== 测试6: 流水线停顿场景 ===")
      
  //     // 请求但AXI未就绪
  //     dut.io.bus.req.bits.addr.poke(0x7000.U)
  //     dut.io.bus.req.valid.poke(true.B)
  //     dut.io.axi_bus.req.ready.poke(false.B) // AXI未就绪
      
  //     dut.clock.step(3) // 等待几个周期
  //     println(s"AXI未就绪时 - ${monitorState(dut)}")
      
  //     // 然后AXI就绪
  //     dut.io.axi_bus.req.ready.poke(true.B)
  //     dut.clock.step(1)
      
  //     // 正常完成传输
  //     dut.io.axi_bus.resp.valid.poke(true.B)
  //     dut.io.axi_bus.resp.bits.data.poke(0x77777777L.U)
  //     dut.clock.step(1)
      
  //     dut.io.bus.resp.valid.expect(true.B)
  //     println("流水线停顿测试通过 ✓")
  //   }
  // }
}