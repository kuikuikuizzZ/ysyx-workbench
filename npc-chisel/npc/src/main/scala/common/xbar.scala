
package npc.common

import chisel3._
import chisel3.util._

import Constants._
import npc.common._
import npc.devices.{ExtendedDevices,DeviceRange}


class AddressDecoder(deviceRanges: Seq[DeviceRange]) extends Module {
    // 确保设备范围不重叠
    deviceRanges.combinations(2).foreach { case Seq(a, b) =>
        assert(
        !a.contains(b.base) && !a.contains(b.end) &&
        !b.contains(a.base) && !b.contains(a.end),
        s"Device ranges overlap: ${a.base}-${a.end} and ${b.base}-${b.end}"
        )
    }
    val numDevices = deviceRanges.size
    val selWidth = if (numDevices == 0) 1 else log2Ceil(numDevices)

    val io = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val valid = Output(Bool())  // 地址是否在任一设备范围内
        val deviceSel = Output(UInt(selWidth.W))  // 选择的设备索引
    })
  
    // 计算每个设备范围的匹配
    val inRange = deviceRanges.map { range =>
        val base = range.base.U(32.W)
        val end = (range.base + range.size - 1).U(32.W)
        // 使用范围检查，而非简单起始地址
        io.addr >= base && io.addr <= end
    }
  
    // 使用优先级编码器处理多个匹配（虽然理论上不应该发生）
    val deviceMatch = VecInit(inRange).asUInt
    io.deviceSel := PriorityEncoder(deviceMatch)
    
    // 验证是否有至少一个设备匹配
    io.valid := deviceMatch.orR
    
    // 可选：添加调试输出
    if (deviceRanges.nonEmpty) {
        printf(p"AddrDecoder: addr=0x${Hexadecimal(io.addr)} valid=${io.valid} " +
            p"deviceSel=${io.deviceSel} ranges: ${deviceRanges.map(_.base).mkString("|")}\n")
    }
}


// class AXIBar(num_masters: Int, num_slaves: Int) (implicit val conf: YSYX24100012Config) extends Module { 
//     val io = IO(new Bundle { 
//         val masters = Vec(num_masters, new AXI4LiteIo)
//         val slaves = Vec(num_slaves, Flipped(new AXI4LiteIo))
//     })

//     val decoder = Seq.fill(num_masters)(Module(new AddressDecoder(ExtendedDevices)))
//     val arbiters = Seq.fill(numSlaves)(Module(new Arbiter(UInt(32.W), numMasters)))

//     val respRouting = RegInit(0.U(num_masters*num_slaves))

//     for (i <- 0 until num_masters) { 
//         decoder(i).io.addr := Mux(io.masters(i).aw.valid, io.masters(i).axi_io.aw.addr, io.masters(i).axi_io.ar.addr)
//         for (i <- 0 untils num_slaves){
//             val addrMatch := decoder(i).io.deviceSel === j.U
//             arbiters(j).io.in(i).valid :=  (io.masters(i).axi_io.ar.valid || io.masters(i).axi_io.ar.valid) && addrMatch && decoders(i).io.valid
//             arbiters(j).io.in(i).bits :=  (io.masters(i).axi_io.ar.addr || io.masters(i).axi_io.ar.valid) && addrMatch && decoders(i).io.valid

//             // slave aready
//             io.masters(i).ar.ready := Mux(addrMatch && decoders(i).io.valid, 
//                                  arbiters(j).io.in(i).ready, 
//                                  false.B)
//             when(arbiters(j).io.in(i).fire) {
//                 // 设置路由位（表示来自主设备i的事务正在访问从设备j）
//                 respRouting := respRouting.bitSet(i * numSlaves + j, true.B)
//             }
//         }
//     }
    
// }


// class AXI4LiteArbiter(numMasters: Int)(implicit val conf: YSYX24100012Config)  extends Module {
//     val io = IO(new Bundle {
//         val masters = Flipped(Vec(numMasters, new AXI4LiteIo()))
//         val slave = new AXI4LiteIo
//     })
//     object State extends ChiselEnum {
//     val Idle, ReadAddress, ReadData, WriteAddress, WriteData, WriteResponse = Value
//     }
//     val state = RegInit(State.Idle)

//     val currentMaster = RegInit(0.U(log2Ceil(numMasters).W))
//     val rrCounter = RegInit(0.U(log2Ceil(numMasters).W))


//     // 写事务状态跟踪
//     val writeActive = RegInit(false.B)
//     val writeMaster = Reg(UInt(log2Ceil(numMasters).W))

//     // 读事务状态跟踪
//     val readActive = RegInit(false.B)
//     val readMaster = Reg(UInt(log2Ceil(numMasters).W))

//     // 默认连接 - 所有主设备未选中
//     for (i <- 0 until numMasters) {
//         io.masters(i).aw.ready := false.B
//         io.masters(i).w.ready := false.B
//         io.masters(i).b.valid := false.B
//         io.masters(i).b.resp := 0.U
//         io.masters(i).ar.ready := false.B
//         io.masters(i).r.valid := false.B
//         io.masters(i).r.data := 0.U
//         io.masters(i).r.resp := 0.U
//     }
  
//     // 从设备接口默认值
//     io.slave.aw.valid := false.B
//     io.slave.aw.addr := 0.U
//     io.slave.w.valid := false.B
//     io.slave.w.data := 0.U
//     io.slave.w.strb := 0.U
//     io.slave.b.ready := false.B
//     io.slave.ar.valid := false.B
//     io.slave.ar.addr := 0.U
//     io.slave.r.ready := false.B
//     val readRequests = VecInit(io.masters.map(_.ar.valid)).asUInt
//     val writeRequests = VecInit(io.masters.map(_.aw.valid)).asUInt

//     switch(state){
//         is(State.Idle){
//             when (readRequests.orR && !writeActive){
//                 currentMaster := PriorityEncoder(readRequests)
//                 state := State.ReadAddress
//             }
//             when (writeRequests.orR && !readActive){
//                 currentMaster := PriorityEncoder(writeRequests)
//                 state := State.WriteAddress
//             }
//         }

//         is(State.ReadAddress){
//             io.slave.ar.valid := io.masters(currentMaster).ar.valid
//             io.masters(currentMaster).ar.ready := io.slave.ar.ready
            
//             io.slave.ar.addr := io.masters(currentMaster).ar.addr
//             when(io.slave.ar.ready && io.slave.ar.valid){
//                 readMaster := currentMaster
//                 readActive := true.B
//                 state := State.ReadData
//             }
//         }

//         is (State.ReadData){
//             io.slave.r.ready := io.masters(currentMaster).r.ready

//             io.masters(currentMaster).r.valid := io.slave.r.valid
//             io.masters(currentMaster).r.data := io.slave.r.data
//             io.masters(currentMaster).r.resp := io.slave.r.resp

//             when(io.masters(currentMaster).r.valid && io.slave.r.ready){
//                 readActive := false.B
//                 state := State.Idle
//                 rrCounter := rrCounter +% 1.U
//             }
//         }

//         is (State.WriteAddress){
//             io.masters(currentMaster).aw.ready := writeAddrReadyReg
//             io.slave.aw.valid := io.masters(currentMaster).aw.valid
//             io.slave.aw.addr := RegEnable(io.masters(currentMaster).aw.addr, state === State.Idle)
      
//             when(io.slave.aw.valid && io.slave.aw.ready){
//                 writeActive := true.B
//                 state := State.WriteData
//                 writeMaster := currentMaster
//             }
//         }
//         is(State.WriteData){
//             writeDataValidReg := io.masters(currentMaster).w.valid
//             io.masters(currentMaster).w.ready := writeDataReadyReg
            
//             io.slave.w.valid := writeDataValidReg
//             io.slave.w.data := RegEnable(io.masters(currentMaster).w.data, state === State.WriteAddress)
//             io.slave.w.strb := RegEnable(io.masters(currentMaster).w.strb, state === State.WriteAddress)
      
//             when(io.slave.w.valid && io.slave.w.ready){
//                 state := State.WriteResponse
//             }
//         }
//         is(State.WriteResponse){
//             writeRespValidReg := io.slave.b.valid
//             io.slave.b.ready := writeRespReadyReg
            
//             io.masters(currentMaster).b.valid := writeRespValidReg
//             io.masters(currentMaster).b.resp := RegEnable(io.slave.b.resp, state === State.WriteData)
//             when(io.slave.b.valid && io.slave.b.ready){
//                 writeActive := false.B
//                 state := State.Idle
//                 rrCounter := rrCounter +% 1.U
//             }
//         }
//     }
// }


class AXI4LiteArbiter(numMasters: Int)(implicit val conf: YSYX24100012Config)  extends Module {
    val io = IO(new Bundle {
        val masters = Flipped(Vec(numMasters, new AXI4LiteIo()))
        val slave = new AXI4LiteIo
    })
    object State extends ChiselEnum {
    val Idle, ReadBusy,WriteBusy = Value
    }
    val state = RegInit(State.Idle)

    val currentMaster = RegInit(0.U(log2Ceil(numMasters).W))
    val rrCounter = RegInit(0.U(log2Ceil(numMasters).W))


    // 写事务状态跟踪
    val writeActive = RegInit(false.B)
    val writeMaster = Reg(UInt(log2Ceil(numMasters).W))

    // 读事务状态跟踪
    val readActive = RegInit(false.B)
    val readMaster = Reg(UInt(log2Ceil(numMasters).W))

    // 默认连接 - 所有主设备未选中
    for (i <- 0 until numMasters) {
        io.masters(i).aw.ready := false.B
        io.masters(i).w.ready := false.B
        io.masters(i).b.valid := false.B
        io.masters(i).b.resp := 0.U
        io.masters(i).ar.ready := false.B
        io.masters(i).r.valid := false.B
        io.masters(i).r.data := 0.U
        io.masters(i).r.resp := 0.U
    }
  
    // 从设备接口默认值
    io.slave.aw.valid := false.B
    io.slave.aw.addr := 0.U
    io.slave.w.valid := false.B
    io.slave.w.data := 0.U
    io.slave.w.strb := 0.U
    io.slave.b.ready := false.B
    io.slave.ar.valid := false.B
    io.slave.ar.addr := 0.U
    io.slave.r.ready := false.B
    val readRequests = VecInit(io.masters.map(_.ar.valid)).asUInt
    val writeRequests = VecInit(io.masters.map(_.aw.valid)).asUInt

    switch(state){
        is(State.Idle){
            when (readRequests.orR ){
                currentMaster := PriorityEncoder(readRequests)
                state := State.ReadBusy
                
            }
            when (writeRequests.orR ){
                currentMaster := PriorityEncoder(writeRequests)
                state := State.WriteBusy
            }
        }

        is(State.ReadBusy){
            io.slave.ar <> io.masters(currentMaster).ar
            io.slave.r <> io.masters(currentMaster).r
            when(io.slave.r.ready&&io.slave.r.valid){
                state := State.Idle    
                rrCounter := rrCounter +% 1.U
                currentMaster := rrCounter
            }
            
        }

        is (State.WriteBusy){
            io.slave.aw <> io.masters(currentMaster).aw
            io.slave.w <> io.masters(currentMaster).w
            io.slave.b <> io.masters(currentMaster).b
            when(io.masters(currentMaster).b.valid && io.slave.b.ready){                state := State.Idle
                rrCounter := rrCounter +% 1.U
                currentMaster := rrCounter
            }
        }

       
    }
}