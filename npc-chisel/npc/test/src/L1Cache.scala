package npc

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import npc._
import npc.common._
import javax.xml.crypto.Data

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
      dut.io.bus.req.bits.waymask.poke("b0001".U) // 选择way 0
      dut.io.bus.req.bits.store.poke(false.B)
      dut.io.bus.req.bits.burst.poke(false.B) // 启用突发传输
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
      val testData = new Array[Int](dut.blockRows)
      for (i <- 0 until dut.blockRows) {
        testData(i) = 0x00A0A0A0 + i
      }
      for (i <- 0 until dut.blockRows) {
        dut.clock.step(1)
        dut.io.axi_bus.resp.valid.poke(true.B)
        dut.io.axi_bus.resp.bits.data.poke(testData(i).U)
        dut.io.axi_bus.resp.bits.resp.poke(0.U) // OKAY
        dut.io.debug.state.expect(1.U)
        dut.clock.step(1)
        if (i == testData.length - 1) {
          dut.io.bus.resp.valid.expect(true.B)
          // 这里应该验证完整的缓存行数据
        }else{
          dut.io.debug.state.expect(0.U)
        }
      }
      println(s"Cycle 3 - ${monitorState(dut)}")
      
      // 验证响应完成
      for (i <-  0 until dut.blockRows) {
        dut.io.bus.resp.bits.data(i).expect(testData(i).U)
      }
      dut.io.bus.resp.valid.expect(true.B)
      dut.io.bus.resp.bits.resp.expect(0.U) // OKAY
      dut.io.bus.resp.bits.addr.expect(testAddr.U)
      dut.io.debug.state.expect(2.U)
      
      println("单次读操作测试通过 ✓")
    }
  }

  it should "handle burst read operations when enabled" in {
    implicit val conf = Config()      
    // 测试突发传输配置    
    simulate(new MissUnit) { dut =>
      println("=== 测试2: 突发读操作 ===")
      
      val testAddr = 0x2000
      dut.io.bus.req.bits.addr.poke(testAddr.U)
      dut.io.bus.req.bits.vaddr.poke(testAddr.U)
      dut.io.bus.req.bits.store.poke(false.B)
      dut.io.bus.req.bits.burst.poke(true.B) // 启用突发传输
      dut.io.bus.req.valid.poke(true.B)
      dut.io.debug.state.expect(0.U)
      
      // AXI准备接收
      dut.io.axi_bus.req.ready.poke(true.B)
      dut.clock.step(1)
      println(s"突发请求后 - ${monitorState(dut)}")
      
      // 验证突发请求参数
      dut.io.axi_bus.req.bits.burst.expect(1.U) // BURST_INCR
      dut.io.axi_bus.req.bits.burstlen.expect(dut.blockIdx)
      dut.io.debug.state.expect(1.U)
      
      // 模拟多个突发传输
      val testData = new Array[Int](dut.blockRows)
      for (i <- 0 until dut.blockRows) {
        testData(i) = 0x00A0A0A0 + i
      }      
      for (i <-  0 until dut.blockRows) {
        dut.io.debug.state.expect(1.U)
        dut.io.axi_bus.resp.valid.poke(true.B)
        dut.io.axi_bus.resp.bits.data.poke(testData(i).U)
        dut.io.axi_bus.resp.bits.last.poke(i == testData.length - 1)
        
        dut.clock.step(1)
        println(s"突发beat $i - ${monitorState(dut)}")
        
        // 检查缓存行构建
        if (i == testData.length - 1) {
          dut.io.bus.resp.valid.expect(true.B)
          // 这里应该验证完整的缓存行数据
        }
      }
      dut.io.debug.state.expect(2.U)
      for (i <- testData.indices) {
        dut.io.bus.resp.bits.data(i).expect(testData(i).U)
      }
      println("突发读操作测试通过 ✓")
    }
  }

  it should "handle store operations correctly" in {
    implicit val conf = Config()      
    simulate(new MissUnit) { dut =>

      println("=== 测试3: 存储操作 ===")
      
      val testAddr = 0x3000
 // 全字节掩码
      val storeData = new Array[Int](dut.blockRows)
      val storeMask = "b0011" 
      for (i <- 0 until dut.blockRows) {
        storeData(i) = 0x11111111 * (i+1)
        dut.io.bus.req.bits.store_data(i).poke(storeData(i).U)
        dut.io.bus.req.bits.store_wmask(i).poke(storeMask.U)
      }
      dut.io.bus.req.bits.addr.poke(testAddr.U)
      dut.io.bus.req.bits.store.poke(true.B)
      dut.io.bus.req.valid.poke(true.B)    
      dut.io.bus.req.bits.burst.poke(true.B)  
      dut.io.axi_bus.req.ready.poke(true.B)
      dut.io.debug.state.expect(0.U)
      
      dut.clock.step(1)
      
      // 验证突发请求参数
      dut.io.axi_bus.req.bits.burst.expect(1.U) // BURST_INCR
      dut.io.axi_bus.req.bits.burstlen.expect(dut.blockIdx)
      dut.io.debug.state.expect(1.U)
      dut.io.axi_bus.req.bits.raddr.expect(testAddr.U)

      dut.clock.step(5)
      val testData = new Array[Int](dut.blockRows)
      for (i <- 0 until dut.blockRows) {
        testData(i) = 0x12340000+0x1111*(i+1)
      }      
      for (i <-  0 until dut.blockRows) {
        dut.io.debug.state.expect(1.U)
        dut.io.axi_bus.resp.valid.poke(true.B)
        dut.io.axi_bus.resp.bits.data.poke(0x12345678.U)
        dut.io.axi_bus.resp.bits.last.poke(i == testData.length - 1)
        dut.clock.step(1)
        println(s"突发beat $i - ${monitorState(dut)}")
        
        // 检查缓存行构建
        if (i == testData.length - 1) {
          dut.io.bus.resp.valid.expect(true.B)
          // 这里应该验证完整的缓存行数据
        }
      }
      for (i <- testData.indices) {
        dut.io.bus.resp.bits.data(i).expect(testData(i).U)
      }
    }
  }

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

class LoadPipeTest extends AnyFlatSpec with Matchers {
  implicit val conf = new Config()
  implicit val config = new TestConfig
  import AddressUtils._
  // 监控流水线状态函数
  def monitorPipelineState(dut: LoadPipe,cycle: Int): String = {
    // val s0 = dut.io.req.valid.peek().litToBoolean
    // val s1 = dut.s1_valid.peek().litToBooleans
    // val s2 = dut.s2_valid.peek().litToBoolean
    // val s1_hit = if (s1) dut.s1_hit.peek().litToBoolean else false
    // val s2_hit = if (s2) dut.s2_hit.peek().litToBoolean else false
    // val s2_miss = if (s2) dut.s2_miss.peek().litToBoolean else false
    
    // s"Cycle = $cycle S0_valid=$s0, S1_valid=$s1, S2_valid=$s2, S1_hit=$s1_hit, S2_hit=$s2_hit, S2_miss=$s2_miss"
    s"Cycle = $cycle"

  }



  "LoadPipe as ICache" should "handle cache hit correctly" in {
    simulate(new LoadPipe) { dut =>
      println("=== 测试1: 缓存命中场景 ===")
      
      val testAddr = 0x12345678L.U
      val testData = 123456789L
      val testIdx = getIdx(testAddr)
      val testTag = getTag(testAddr)
      
      // 初始化信号
      dut.io.req.valid.poke(false.B)
      dut.io.lsu_s1_kill.poke(false.B)
      dut.io.metaRead.resp.valid.poke(false.B)
      dut.io.dataRead.resp.valid.poke(false.B)
      dut.io.metaRead.req.ready.poke(false.B)
      dut.clock.step(1)
      
      // 阶段0: 发送读取请求
      dut.io.req.valid.poke(true.B)
      dut.io.req.bits.addr.poke(testAddr)
      dut.io.req.bits.rw.poke(false.B) // 读操作
      dut.io.metaRead.req.ready.poke(true.B)
      dut.io.debug.s0_valid.expect(true.B)
      
      // 模拟metaRead就绪
      dut.clock.step(1)
      dut.io.metaRead.req.valid.expect(true.B)
      dut.io.metaRead.req.bits.index.expect(testIdx)
      dut.clock.step(1)
      println(monitorPipelineState(dut,1))
      // 阶段1: 提供meta响应，模拟命中
      dut.io.req.valid.poke(false.B) // 请求只持续一拍
      dut.io.metaRead.resp.valid.poke(true.B)
      for (i <- 0 until dut.nWays) {
        dut.io.metaRead.resp.bits.data(i).tag.poke(testTag)
        dut.io.metaRead.resp.bits.data(i).valid.poke((i == 0).B)
      }
      dut.io.debug.s1_valid.expect(true.B)
      dut.io.debug.s1_fire.expect(true.B)
      dut.io.dataRead.req.valid.expect(true.B)
      // 设置所有way的meta，但只有way0命中
      dut.clock.step(1)
      // for (i <- 0 until dut.nWays) {
      //   // dut.io.debug.s1_hit_vec(i).expect((i == 0).B)
      //   println(s"Way $i hit: " + dut.io.debug.s1_hit_vec(i).peek().litToBoolean)
      //   println("Way $i valid " +  dut.io.debug.s1_meta(i).valid.peek().litToBoolean + " tag: " + dut.io.debug.s1_meta(i).tag.peek().litValue + " testTag " + testTag)
      //   println("s1_tag_match_ways: " + dut.io.debug.s1_tag_match_way(i).peek().litValue)
      // }
      dut.io.debug.s2_valid.expect(true.B)
      dut.io.dataRead.req.bits.index.expect(testIdx)
      println(monitorPipelineState(dut, 2))
      
      // 阶段2: 提供data响应并检查结果
      dut.io.metaRead.resp.valid.poke(false.B)
      dut.io.dataRead.resp.valid.poke(true.B)
      
      // 设置数据，way0有测试数据，其他为0
      val responseData = Seq.tabulate(dut.nWays)(i => 
        if (i == 0) testData.U else 0.U
      )
      for (i <- 0 until dut.nWays) {
        dut.io.dataRead.resp.bits.data(i).data.poke(responseData(i))
      }

      
      dut.clock.step(1)
      println(monitorPipelineState(dut, 3))
      
      // 验证响应
      dut.io.resp.valid.expect(true.B)
      dut.io.resp.bits.miss.expect(false.B)
      for (i <- 0 until dut.blockRows) {
        if (i == 0) {
          dut.io.resp.bits.data(i).expect(testData.U)
        } else {
          dut.io.resp.bits.data(i).expect(0.U)
        }
      }
      
      println("缓存命中测试通过 ✓")
    }
  }

  it should "handle cache miss correctly and trigger miss request" in {
    simulate(new LoadPipe) { dut =>
      println("=== 测试2: 缓存未命中场景 ===")
      
      val testAddr = 0x12345678L
      val testIdx = getIdx(testAddr.U)
      val wrongTag = getTag(0x22345678L.U) // 错误标签导致未命中
      val testData = 0x12345678L
      // 初始化
      dut.io.req.valid.poke(false.B)
      dut.io.lsu_s1_kill.poke(false.B)
      dut.io.metaRead.resp.valid.poke(false.B)
      dut.clock.step(1)
      
      // 发送请求
      dut.io.req.valid.poke(true.B)
      dut.io.req.bits.addr.poke(testAddr.U)
      dut.io.req.bits.rw.poke(false.B)
      dut.io.metaRead.req.ready.poke(true.B)
      
      dut.clock.step(1)
      println(monitorPipelineState(dut, 1))
      
      // 提供meta响应，所有way都不匹配
      dut.io.req.valid.poke(false.B)
      dut.io.metaRead.resp.valid.poke(true.B)
      for (i <- 0 until config.nWays) {
        dut.io.metaRead.resp.bits.data(i).tag.poke(wrongTag) // 所有标签都不匹配
        dut.io.metaRead.resp.bits.data(i).valid.poke(true.B)
      }
      // replace 
      dut.io.replace_way.idx.valid.expect(true.B)
      dut.io.replace_way.idx.bits.expect(testIdx)
      dut.io.replace_way.way.poke(0.U)

      dut.clock.step(1)
      println(monitorPipelineState(dut, 2))
      
      // 检查未命中信号和missBus请求
      dut.io.missBus.req.valid.expect(true.B)
      dut.io.missBus.req.bits.addr.expect(testAddr.U)
      
      dut.clock.step(1)
      dut.io.missBus.resp.valid.poke(true.B)
      for (i <- 0 until dut.blockRows) {
        if (i == 0) {
          dut.io.missBus.resp.bits.data(i).poke(testData.U)
        } else {
          dut.io.missBus.resp.bits.data(i).poke(0.U)
        }
      }

      dut.io.resp.valid.expect(true.B)
      dut.io.resp.bits.miss.expect(true.B)
      for (i <- 0 until dut.blockRows) {
        if (i == 0) {
          dut.io.resp.bits.data(i).expect(testData.U)
        } else {
          dut.io.resp.bits.data(i).expect(0.U)
        }
      }
      println("缓存未命中测试通过 ✓")
    }
  }

  // it should "handle pipeline flush correctly with lsu_s1_kill" in {
  //   test(new LoadPipe).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  //     println("=== 测试3: 流水线清除测试 ===")
      
  //     val testAddr = 0x3000L
      
  //     // 初始化
  //     dut.io.req.valid.poke(false.B)
  //     dut.io.lsu_s1_kill.poke(false.B)
  //     dut.io.metaRead.resp.valid.poke(false.B)
  //     dut.clock.step(1)
      
  //     // 阶段0: 发送请求
  //     dut.io.req.valid.poke(true.B)
  //     dut.io.req.bits.addr.poke(testAddr.U)
  //     dut.io.metaRead.req.ready.poke(true.B)
  //     dut.clock.step(1)
  //     println(s"Cycle 1: " + monitorPipelineState(dut, 1))
      
  //     // 阶段1: 激活清除信号
  //     dut.io.req.valid.poke(false.B)
  //     dut.io.metaRead.resp.valid.poke(true.B)
  //     dut.io.lsu_s1_kill.poke(true.B) // 清除流水线
  //     dut.clock.step(1)
  //     println(s"Cycle 2: " + monitorPipelineState(dut, 2))
      
  //     // 阶段2: 检查流水线被清除
  //     dut.io.lsu_s1_kill.poke(false.B)
  //     dut.io.metaRead.resp.valid.poke(false.B)
  //     dut.clock.step(1)
  //     println(s"Cycle 3: " + monitorPipelineState(dut, 3))
      
  //     // 验证响应无效（因为被清除了）
  //     dut.io.resp.valid.expect(false.B)
  //     println("流水线清除测试通过 ✓")
  //   }
  // }

  // it should "handle back-to-back requests efficiently" in {
  //   test(new LoadPipe).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  //     println("=== 测试4: 背靠背请求测试 ===")
      
  //     val addresses = Seq(0x4000L, 0x4000L, 0x5000L) // 测试地址序列
  //     val testData = 0xABCD1234L
      
  //     // 初始化
  //     dut.io.req.valid.poke(false.B)
  //     dut.io.lsu_s1_kill.poke(false.B)
  //     dut.io.metaRead.resp.valid.poke(false.B)
  //     dut.io.dataRead.resp.valid.poke(false.B)
  //     dut.io.metaRead.req.ready.poke(true.B)
  //     dut.io.dataRead.req.ready.poke(true.B)
  //     dut.clock.step(1)
      
  //     var responsesReceived = 0
      
  //     // 发送背靠背请求
  //     for ((addr, i) <- addresses.zipWithIndex) {
  //       // 发送请求
  //       dut.io.req.valid.poke(true.B)
  //       dut.io.req.bits.addr.poke(addr.U)
  //       dut.clock.step(1)
  //       println(s"请求 $i 发送: addr=0x${addr.toHexString}")
        
  //       dut.io.req.valid.poke(false.B)
        
  //       // 提供meta响应（总是命中）
  //       dut.io.metaRead.resp.valid.poke(true.B)
  //       val tag = getTag(addr.U)
  //       for (way <- 0 until conf.nWays) {
  //         dut.io.metaRead.resp.bits.data(way).tag.poke(tag)
  //         dut.io.metaRead.resp.bits.data(way).valid.poke((way == 0).B)
  //       }
  //       dut.clock.step(1)
        
  //       // 提供data响应
  //       dut.io.metaRead.resp.valid.poke(false.B)
  //       dut.io.dataRead.resp.valid.poke(true.B)
  //       dut.io.dataRead.resp.bits.data.poke(
  //         VecInit(Seq.fill(conf.nWays)(testData.U))
  //       )
  //       dut.clock.step(1)
        
  //       // 检查响应
  //       if (dut.io.resp.valid.peek().litToBoolean) {
  //         dut.io.resp.bits.data.expect(testData.U)
  //         responsesReceived += 1
  //       }
        
  //       dut.io.dataRead.resp.valid.poke(false.B)
  //     }
      
  //     // 验证所有请求都得到响应
  //     responsesReceived should be(addresses.length)
  //     println(s"背靠背请求测试通过，收到 $responsesReceived 个响应 ✓")
  //   }
  // }

  // it should "maintain correct timing for icache read operations" in {
  //   test(new LoadPipe).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  //     println("=== 测试5: ICache读取时序验证 ===")
      
  //     // 验证作为icache的关键时序特性
  //     val testAddr = 0x6000L
      
  //     dut.io.req.valid.poke(false.B)
  //     dut.io.lsu_s1_kill.poke(false.B)
  //     dut.clock.step(1)
      
  //     // 测量从请求到响应的延迟
  //     dut.io.req.valid.poke(true.B)
  //     dut.io.req.bits.addr.poke(testAddr.U)
  //     dut.io.req.bits.rw.poke(false.B) // 必须为读操作
      
  //     var cycles = 0
  //     var responseReceived = false
      
  //     // 模拟理想情况：所有资源就绪
  //     dut.io.metaRead.req.ready.poke(true.B)
  //     dut.io.dataRead.req.ready.poke(true.B)
      
  //     while (cycles < 10 && !responseReceived) {
  //       // 在适当周期提供响应
  //       if (cycles == 1) {
  //         dut.io.metaRead.resp.valid.poke(true.B)
  //         val tag = getTag(testAddr.U)
  //         for (i <- 0 until conf.nWays) {
  //           dut.io.metaRead.resp.bits.data(i).tag.poke(tag)
  //           dut.io.metaRead.resp.bits.data(i).valid.poke(true.B)
  //         }
  //       } else if (cycles == 2) {
  //         dut.io.metaRead.resp.valid.poke(false.B)
  //         dut.io.dataRead.resp.valid.poke(true.B)
  //         dut.io.dataRead.resp.bits.data.poke(
  //           VecInit(Seq.fill(conf.nWays)(0xDEADBEEFL.U))
  //         )
  //       }
        
  //       if (dut.io.resp.valid.peek().litToBoolean) {
  //         responseReceived = true
  //         println(s"响应在周期 $cycles 收到")
  //         // ICache读取应该在一定周期内完成（典型3周期流水线）
  //         cycles should be(3) // S0->S1->S2
  //       }
        
  //       dut.clock.step(1)
  //       cycles += 1
  //     }
      
  //     responseReceived should be(true)
  //     println("ICache时序验证通过 ✓")
  //   }
  // }
}