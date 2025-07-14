
package npc.common

import chisel3._
import chisel3.util._

import npc.common.CSR._
// import npc.common._

import Constants._
trait MemoryOpConstants 
{
   val MT_X  = 0.asUInt(3.W)
   val MT_B  = 1.asUInt(3.W)
   val MT_H  = 2.asUInt(3.W)
   val MT_W  = 3.asUInt(3.W)
   val MT_D  = 4.asUInt(3.W)
   val MT_BU = 5.asUInt(3.W)
   val MT_HU = 6.asUInt(3.W)
   val MT_WU = 7.asUInt(3.W)

   val M_X   = "b0".asUInt(1.W)
   val M_XRD = "b0".asUInt(1.W) // int load
   val M_XWR = "b1".asUInt(1.W) // int store

   val DPORT = 0
   val IPORT = 1
}

class Wport(val addrWidth : Int,val dataWidth : Int) extends Bundle{
   val addr = Input(UInt(addrWidth.W))
   val data = Input(UInt(dataWidth.W))
   val len = Input(UInt(dataWidth.W))
   val en = Input(Bool())
}


class Rport(val addrWidth : Int,val dataWidth : Int) extends Bundle{
   val addr = Input(UInt(addrWidth.W))
   val data = Output(UInt(dataWidth.W))
   val en = Input(Bool())
}

class MemIo(val addrWidth: Int) extends Bundle
{
   val dataInstr = Vec(2, new Rport(addrWidth,32))
   val dw = new  Wport(addrWidth,32)
}




// from the pov of the datapath
class MemPortIo(val data_width: Int)(implicit val conf: YSYX24100012Config) extends Bundle 
{
   val req    = new DecoupledIO(new MemReq(data_width))
   val resp   = Flipped(new ValidIO(new MemResp(data_width)))
}

class MemReq(val data_width: Int)(implicit val conf: YSYX24100012Config) extends Bundle
{
   val addr = Output(UInt(conf.xprlen.W))
   val data = Output(UInt(data_width.W))
   val fcn  = Output(UInt(M_X.getWidth.W))  // memory function code
   val typ  = Output(UInt(MT_X.getWidth.W)) // memory type
}

class MemResp(val data_width: Int) extends Bundle
{
   val data = Output(UInt(data_width.W))
}

class YSYX2400012AsyncPadMem(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dataInstr = Vec(2, new Rport(addrWidth,32))
      val dw = new  Wport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012AsyncPadMem.v"
   addPath(path)
   println(s"YSYX2400012AsyncPadMem path: ${path}")
}

class YSYX2400012SyncPadMem(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dataInstr = Vec(2, new Rport(addrWidth,32))
      val dw = new  Wport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012SyncPadMem.v"
   addPath(path)
   println(s"YSYX2400012SyncPadMem path: ${path}")
}

class YSYX2400012SyncMemHalf(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dataInstr = Vec(2, new Rport(addrWidth,32))
      val dw = new  Wport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012SyncMemHalf.v"
   addPath(path)
   println(s"YSYX2400012SyncMemHalf path: ${path}")
}

class YSYX2400012SyncMem(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dr = new Rport(addrWidth,32)
      val dw = new  Wport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012SyncMem.v"
   addPath(path)
   println(s"YSYX2400012SyncMem path: ${path}")
}

class YSYX2400012AsyncMem(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dr = new Rport(addrWidth,32)
      val dw = new Wport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012AsyncMem.v"
   addPath(path)
   println(s"YSYX2400012AsyncMem path: ${path}")
}

class YSYX2400012AXI4LiteMem(implicit val conf: YSYX24100012Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012AXI4LiteMem.v"
   addPath(path)
   println(s"YSYX2400012AsyncMem path: ${path}")
}

class YSYX2400012AXI4LiteMemRandomDelay(implicit val conf: YSYX24100012Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012AXI4LiteMemRandomDelay.v"
   addPath(path)
   println(s"YSYX2400012AXI4LiteMemRandomDelay path: ${path}")
}


class YSYX2400012AXI4LiteMemRandomDelayData(implicit val conf: YSYX24100012Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/YSYX2400012AXI4LiteMemRandomDelayData.v"
   addPath(path)
   println(s"YSYX2400012AXI4LiteMemRandomDelay path: ${path}")
}

class AsyncScratchPadMemory(val num_core_ports: Int,val num_bytes: Int = (1 << 21))(implicit val conf: YSYX24100012Config) extends Module
{
   val io = IO(new Bundle
   {
      val core_ports = Vec(num_core_ports, Flipped(new MemPortIo(data_width = conf.xprlen)) )
   })
   val num_bytes_per_line = 8
   val num_lines = num_bytes / num_bytes_per_line
   val async_data = Module(new YSYX2400012AsyncPadMem(32))
   async_data.io.clock := clock
   async_data.io.reset := reset
   for (i <- 0 until num_core_ports)
   {
      io.core_ports(i).resp.valid := io.core_ports(i).req.valid
      io.core_ports(i).req.ready := true.B // for now, no back pressure 
      async_data.io.dataInstr(i).addr := io.core_ports(i).req.bits.addr
   }

   /////////// DPORT 
   val req_addri = io.core_ports(DPORT).req.bits.addr
   val req_typi = io.core_ports(DPORT).req.bits.typ
   val resp_datai = async_data.io.dataInstr(DPORT).data
   async_data.io.dataInstr(DPORT).en := io.core_ports(DPORT).req.valid
   io.core_ports(DPORT).resp.bits.data := MuxCase(resp_datai,Seq(
      (req_typi === MT_B) -> Cat(Fill(24,resp_datai(7)),resp_datai(7,0)),
      (req_typi === MT_H) -> Cat(Fill(16,resp_datai(15)),resp_datai(15,0)),
      (req_typi === MT_BU) -> Cat(Fill(24,0.U),resp_datai(7,0)),
      (req_typi === MT_HU) -> Cat(Fill(16,0.U),resp_datai(15,0))
   ))
   async_data.io.dw := DontCare
   async_data.io.dw.en := false.B
   when (io.core_ports(DPORT).req.valid && (io.core_ports(DPORT).req.bits.fcn === M_XWR))
   {
      async_data.io.dw.en := true.B
      // 这里是配合下面 mask 设计的，目的让数据靠左对齐，0000 0001 -> 0001 0000
      // async_data.io.dw.data := io.core_ports(DPORT).req.bits.data << (req_addri(1,0) << 3)
      async_data.io.dw.data := io.core_ports(DPORT).req.bits.data 
      async_data.io.dw.addr := req_addri
      async_data.io.dw.len := Mux(req_typi === MT_B,1.U,
                              Mux(req_typi === MT_H,2.U,4.U))
   }
   /////////////////

   ///////////// IPORT
   if (num_core_ports == 2){
      async_data.io.dataInstr(IPORT).en := io.core_ports(IPORT).req.valid
      io.core_ports(IPORT).resp.bits.data := async_data.io.dataInstr(IPORT).data
   }
   ////////////
 
}

class SyncScratchPadMemory(num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit val conf: YSYX24100012Config) extends Module
{
   val io = IO(new Bundle
   {
      val core_ports = Vec(num_core_ports, Flipped(new MemPortIo(data_width = conf.xprlen)) )
   })
   val num_bytes_per_line = 8
   val num_lines = num_bytes / num_bytes_per_line
   val sync_data = Module(new YSYX2400012SyncMemHalf(32))
   sync_data.io.clock := clock
   sync_data.io.reset := reset
   
   io.core_ports(IPORT).resp.valid := RegNext(io.core_ports(IPORT).req.valid,false.B)
   io.core_ports(DPORT).resp.valid := io.core_ports(DPORT).req.valid

   for (i <- 0 until num_core_ports)
   {
      io.core_ports(i).req.ready := true.B // for now, no back pressure
      sync_data.io.dataInstr(i).addr := io.core_ports(i).req.bits.addr
   }

   /////////// DPORT
   //val resp_datai = Wire(UInt(conf.xprlen.W))
   val req_addri = io.core_ports(DPORT).req.bits.addr
   val req_typi = Reg(UInt(3.W))
   req_typi := io.core_ports(DPORT).req.bits.typ
   val resp_datai = sync_data.io.dataInstr(DPORT).data
   sync_data.io.dataInstr(DPORT).en := io.core_ports(DPORT).req.valid

   io.core_ports(DPORT).resp.bits.data := MuxCase(resp_datai,Seq(
      (req_typi === MT_B) -> Cat(Fill(24,resp_datai(7)),resp_datai(7,0)),
      (req_typi === MT_H) -> Cat(Fill(16,resp_datai(15)),resp_datai(15,0)),
      (req_typi === MT_BU) -> Cat(Fill(24,0.U),resp_datai(7,0)),
      (req_typi === MT_HU) -> Cat(Fill(16,0.U),resp_datai(15,0))
   ))
   sync_data.io.dw := DontCare
   sync_data.io.dw.en := io.core_ports(DPORT).req.bits.fcn === M_XWR

   when (io.core_ports(DPORT).req.valid && (io.core_ports(DPORT).req.bits.fcn === M_XWR))
   {
      // sync_data.io.dw.data := io.core_ports(DPORT).req.bits.data << (req_addri(1,0) << 3)
      // sync_data.io.dw.addr := Cat(req_addri(31,2),0.asUInt(2.W))
      // sync_data.io.dw.len := Mux(req_typi === MT_B,1.U,
      //                         Mux(req_typi === MT_H,2.U,4.U))
      sync_data.io.dw.data := io.core_ports(DPORT).req.bits.data 
      sync_data.io.dw.addr := req_addri
      sync_data.io.dw.len := Mux(req_typi === MT_B,1.U,
                              Mux(req_typi === MT_H,2.U,4.U))                 
   }
   /////////////////

   ///////////// IPORT
   if (num_core_ports == 2)
      sync_data.io.dataInstr(IPORT).en := io.core_ports(IPORT).req.valid
      io.core_ports(IPORT).resp.bits.data := sync_data.io.dataInstr(IPORT).data
   ////////////

}

class SyncMemory(num_bytes: Int = (1 << 21))(implicit val conf: YSYX24100012Config) extends Module
{
   val io = IO(new Bundle
   {
      val port = Flipped(new MemPortIo(data_width = conf.xprlen))
   })

   val sync_data = Module(new YSYX2400012SyncMem(32))
   sync_data.io.clock := clock
   sync_data.io.reset := reset
   
   io.port.resp.valid := RegNext(io.port.req.valid,false.B)
   io.port.req.ready := true.B // for now, no back pressure
   sync_data.io.dr.addr := io.port.req.bits.addr
   
   /////////// Read Port
   val req_addri = io.port.req.bits.addr
   val req_typi = Reg(UInt(3.W))
   req_typi := io.port.req.bits.typ
   val resp_datai = sync_data.io.dr.data
   sync_data.io.dr.en := io.port.req.valid

   io.port.resp.bits.data := MuxCase(resp_datai,Seq(
      (req_typi === MT_B) -> Cat(Fill(24,resp_datai(7)),resp_datai(7,0)),
      (req_typi === MT_H) -> Cat(Fill(16,resp_datai(15)),resp_datai(15,0)),
      (req_typi === MT_BU) -> Cat(Fill(24,0.U),resp_datai(7,0)),
      (req_typi === MT_HU) -> Cat(Fill(16,0.U),resp_datai(15,0))
   ))

   /////////// Write Port
   sync_data.io.dw := DontCare
   sync_data.io.dw.en := io.port.req.bits.fcn === M_XWR

   when (io.port.req.valid && (io.port.req.bits.fcn === M_XWR))
   {
      sync_data.io.dw.data := io.port.req.bits.data 
      sync_data.io.dw.addr := req_addri
      sync_data.io.dw.len := Mux(req_typi === MT_B,1.U,
                              Mux(req_typi === MT_H,2.U,4.U))  
   }
   /////////////////
}

class AsyncMemory(num_bytes: Int = (1 << 21))(implicit val conf: YSYX24100012Config) extends Module
{
   val io = IO(new Bundle
   {
      val port = Flipped(new MemPortIo(data_width = conf.xprlen))
   })

   val async_data = Module(new YSYX2400012AsyncMem(32))
   async_data.io.clock := clock
   async_data.io.reset := reset
   
   io.port.resp.valid := io.port.req.valid
   io.port.req.ready := true.B // for now, no back pressure
   
   /////////// Read Port

   val req_addri = io.port.req.bits.addr
   val req_typi = Wire(UInt(3.W))
   req_typi := io.port.req.bits.typ
   async_data.io.dr.addr := io.port.req.bits.addr
   async_data.io.dr.en := io.port.req.valid
   val resp_datai = async_data.io.dr.data

   io.port.resp.bits.data := MuxCase(resp_datai,Seq(
      (req_typi === MT_B) -> Cat(Fill(24,resp_datai(7)),resp_datai(7,0)),
      (req_typi === MT_H) -> Cat(Fill(16,resp_datai(15)),resp_datai(15,0)),
      (req_typi === MT_BU) -> Cat(Fill(24,0.U),resp_datai(7,0)),
      (req_typi === MT_HU) -> Cat(Fill(16,0.U),resp_datai(15,0))
   ))

   /////////// Write Port
   async_data.io.dw := DontCare
   async_data.io.dw.en := io.port.req.bits.fcn === M_XWR

   when (io.port.req.valid && (io.port.req.bits.fcn === M_XWR))
   {
      async_data.io.dw.data := io.port.req.bits.data 
      async_data.io.dw.addr := req_addri
      async_data.io.dw.len := Mux(req_typi === MT_B,1.U,
                              Mux(req_typi === MT_H,2.U,4.U))  
   }
   /////////////////
}

class AXI4LiteMemeory(num_bytes: Int = (1 << 21))(implicit val conf: YSYX24100012Config) extends Module
{
   val io = IO(new Bundle
   {
      val port = Flipped(new MemPortIo(data_width = conf.xprlen))
   }) 
   io := DontCare

   val axi4lite_mem = Module(new AXI4LiteMaster)
   val axi_slave = Module(new AXI4LiteSlave())

   io.port.req.ready := RegInit(true.B)
   axi4lite_mem.io := DontCare
   axi_slave.io := DontCare
   axi4lite_mem.io.clock  := clock
   axi4lite_mem.io.reset := reset
   axi_slave.clock := clock
   axi_slave.reset := reset

   axi4lite_mem.io.axi_io <> axi_slave.io.axi_io
   axi4lite_mem.io.req.raddr := io.port.req.bits.addr
   axi4lite_mem.io.req.wen := Mux(io.port.req.valid,io.port.req.bits.fcn === M_XWR, false.B)
   axi4lite_mem.io.req.ren := Mux(io.port.req.valid,io.port.req.bits.fcn === M_XRD, false.B)

   /////////// Read Port
   val resp_datai = axi4lite_mem.io.resp.bits.data
   val req_typi = Wire(UInt(3.W))
   val req_addri = io.port.req.bits.addr
   req_typi := io.port.req.bits.typ
   io.port.resp.bits.data := MuxCase(resp_datai,Seq(
      (req_typi === MT_B) -> Cat(Fill(24,resp_datai(7)),resp_datai(7,0)),
      (req_typi === MT_H) -> Cat(Fill(16,resp_datai(15)),resp_datai(15,0)),
      (req_typi === MT_BU) -> Cat(Fill(24,0.U),resp_datai(7,0)),
      (req_typi === MT_HU) -> Cat(Fill(16,0.U),resp_datai(15,0))
   ))
   
   /////////// Write Port
   when (io.port.req.valid && (io.port.req.bits.fcn === M_XWR)){
      // axi4lite_mem.io.req.waddr := req_addri
      axi4lite_mem.io.req.data := io.port.req.bits.data<< (req_addri(1,0) << 3)
      axi4lite_mem.io.req.waddr := Cat(req_addri(31,2),0.asUInt(2.W))
      axi4lite_mem.io.req.mask := Mux(req_typi === MT_B,1.U << req_addri(1,0),
                              Mux(req_typi === MT_H,3.U << req_addri(1,0),15.U))
   }
   io.port.resp.valid := axi4lite_mem.io.resp.valid
}

class AXI4LiteMemeoryData(num_bytes: Int = (1 << 21))(implicit val conf: YSYX24100012Config) extends Module
{
   val io = IO(new Bundle
   {
      val port = Flipped(new MemPortIo(data_width = conf.xprlen))
   }) 
   io := DontCare

   val axi4lite_mem = Module(new AXI4LiteMasterData)
   val axi_slave = Module(new AXI4LiteSlaveData())

   io.port.req.ready := RegInit(true.B)
   axi4lite_mem.io := DontCare
   axi_slave.io := DontCare
   axi4lite_mem.io.clock  := clock
   axi4lite_mem.io.reset := reset
   axi_slave.clock := clock
   axi_slave.reset := reset

   axi4lite_mem.io.axi_io <> axi_slave.io.axi_io
   axi4lite_mem.io.req.raddr := io.port.req.bits.addr
   axi4lite_mem.io.req.wen := Mux(io.port.req.valid,io.port.req.bits.fcn === M_XWR, false.B)
   axi4lite_mem.io.req.ren := Mux(io.port.req.valid,io.port.req.bits.fcn === M_XRD, false.B)

   /////////// Read Port
   val resp_datai = axi4lite_mem.io.resp.bits.data
   val req_typi = Wire(UInt(3.W))
   val req_addri = io.port.req.bits.addr
   req_typi := io.port.req.bits.typ
   io.port.resp.bits.data := MuxCase(resp_datai,Seq(
      (req_typi === MT_B) -> Cat(Fill(24,resp_datai(7)),resp_datai(7,0)),
      (req_typi === MT_H) -> Cat(Fill(16,resp_datai(15)),resp_datai(15,0)),
      (req_typi === MT_BU) -> Cat(Fill(24,0.U),resp_datai(7,0)),
      (req_typi === MT_HU) -> Cat(Fill(16,0.U),resp_datai(15,0))
   ))
   
   /////////// Write Port
   when (io.port.req.valid && (io.port.req.bits.fcn === M_XWR)){
      // axi4lite_mem.io.req.waddr := req_addri
      axi4lite_mem.io.req.data := io.port.req.bits.data<< (req_addri(1,0) << 3)
      axi4lite_mem.io.req.waddr := Cat(req_addri(31,2),0.asUInt(2.W))
      axi4lite_mem.io.req.mask := Mux(req_typi === MT_B,1.U << req_addri(1,0),
                              Mux(req_typi === MT_H,3.U << req_addri(1,0),15.U))
   }
   io.port.resp.valid := axi4lite_mem.io.resp.valid
}

