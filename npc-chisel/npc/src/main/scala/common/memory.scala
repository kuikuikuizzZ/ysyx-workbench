
package npc.common

import chisel3._
import chisel3.util._

import npc.common.CSR._

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
class MemPortIo(val data_width: Int)(implicit val conf: ysyx_24100012_Config) extends Bundle 
{
   val req    = new DecoupledIO(new MemReq(data_width))
   val resp   = Flipped(new ValidIO(new MemResp(data_width)))
}

class MemReq(val data_width: Int)(implicit val conf: ysyx_24100012_Config) extends Bundle
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


class ysyx_24100012_SyncMem(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dr = new Rport(addrWidth,32)
      val dw = new  Wport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/ysyx_24100012_SyncMem.v"
   addPath(path)
   println(s"ysyx_24100012_SyncMem path: ${path}")
}

class ysyx_24100012_AsyncMem(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dr = new Rport(addrWidth,32)
      val dw = new Wport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/ysyx_24100012_AsyncMem.v"
   addPath(path)
   println(s"ysyx_24100012_AsyncMem path: ${path}")
}

class ysyx_24100012_SyncMemory(num_bytes: Int = (1 << 21))(implicit val conf: ysyx_24100012_Config) extends Module
{
   val io = IO(new Bundle
   {
      val port = Flipped(new MemPortIo(data_width = conf.xprlen))
   })

   val sync_data = Module(new ysyx_24100012_SyncMem(32))
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

class ysyx_24100012_AsyncMemory(num_bytes: Int = (1 << 21))(implicit val conf: ysyx_24100012_Config) extends Module
{
   val io = IO(new Bundle
   {
      val port = Flipped(new MemPortIo(data_width = conf.xprlen))
   })

   val async_data = Module(new ysyx_24100012_AsyncMem(32))
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

class ysyx_24100012_AXI4LiteMemeory(num_bytes: Int = (1 << 21))(implicit val conf: ysyx_24100012_Config) extends Module
{
   val io = IO(new Bundle
   {
      val port = Flipped(new MemPortIo(data_width = conf.xprlen))
      val axi_port = new AXI4LiteIo()
   }) 
   io := DontCare

   val axi4lite_mem = Module(new ysyx_24100012_AXI4LiteMaster)

   io.port.req.ready := RegInit(true.B)
   axi4lite_mem.io := DontCare
   axi4lite_mem.io.clock  := clock
   axi4lite_mem.io.reset := reset
   axi4lite_mem.io.axi_io <> io.axi_port

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

class ysyx_24100012_AXI4LiteArbiter(numMasters: Int)(implicit val conf: ysyx_24100012_Config)  extends Module {
    val io = IO(new Bundle
   {
      val ports = Flipped(Vec(numMasters,new MemPortIo(data_width = conf.xprlen)))
      val axi_port = new AXI4LiteIo()
      val ifu_valid = Input(Bool())
      val finish = Input(Bool())
      val mem_en = Input(Bool())
   }) 
   io := DontCare
   val s_idle :: s_ifu_active :: s_lsu_active :: Nil = Enum(3)
   val state = RegInit(s_idle)

   val axi4lite_mem = Module(new ysyx_24100012_AXI4LiteMaster)
   axi4lite_mem.io := DontCare
   

   val req_addri    = Wire(UInt(conf.xprlen.W))
   val req_fcn      = Wire(UInt(M_X.getWidth.W))
   val req_typi     = Wire(UInt(MT_X.getWidth.W))
   val req_valid    = Wire(Bool())
   val req_data     = Wire(UInt(conf.xlen.W))
   // val resp_valid   = Wire(Bool())
   val resp_data    = Wire(UInt(conf.xlen.W))
   switch(state) {
      is(s_idle) {
         // IFU优先级高于LSU
         when (io.ports(IPORT).req.valid) {
            state := s_ifu_active
            req_typi := io.ports(IPORT).req.bits.typ
         } .elsewhen (io.ports(DPORT).req.valid) {
            state := s_lsu_active
            req_typi := io.ports(DPORT).req.bits.typ
         }
      }
      is (s_ifu_active){
         when (axi4lite_mem.io.resp.valid) {
            state := s_idle
            req_valid := false.B     
         }
      }
      is (s_lsu_active)  { 
         when (axi4lite_mem.io.resp.valid) {
            state := s_idle
            req_valid := false.B        
         }
      } 
   }

   req_valid := Mux(io.ports(IPORT).req.valid,io.ports(IPORT).req.valid,
                  Mux(io.ports(DPORT).req.valid,io.ports(DPORT).req.valid,false.B))
   req_fcn := Mux(io.ports(IPORT).req.valid,io.ports(IPORT).req.bits.fcn,
                  Mux(io.ports(DPORT).req.valid,io.ports(DPORT).req.bits.fcn,M_X))
   req_typi := Mux(io.ports(IPORT).req.valid,io.ports(IPORT).req.bits.typ,
                  Mux(io.ports(DPORT).req.valid,io.ports(DPORT).req.bits.typ,MT_X))
   req_addri := Mux(io.ports(IPORT).req.valid,io.ports(IPORT).req.bits.addr,
                  Mux(io.ports(DPORT).req.valid,io.ports(DPORT).req.bits.addr,0.U))
   req_data := Mux(io.ports(IPORT).req.valid,io.ports(IPORT).req.bits.data,
                  Mux(io.ports(DPORT).req.valid,io.ports(DPORT).req.bits.data,0.U))
   
   val axi_resp = axi4lite_mem.io.resp.bits.resp
   val resp_valid = axi_resp === 0.U
   val aligned_req_addri = Cat(req_addri(31,2),0.asUInt(2.W))
   io.ports(IPORT).resp.bits.data := Mux(state === s_ifu_active,resp_data,0.U)
   io.ports(DPORT).resp.bits.data := Mux(state === s_lsu_active,resp_data,0.U)
   io.ports(IPORT).resp.valid    := Mux(state === s_ifu_active&&resp_valid,axi4lite_mem.io.resp.valid,false.B)  
   io.ports(DPORT).resp.valid    := Mux(state === s_lsu_active&&resp_valid,axi4lite_mem.io.resp.valid,false.B)  
   axi4lite_mem.io.clock  := clock
   axi4lite_mem.io.reset := reset
   axi4lite_mem.io.axi_io <> io.axi_port

   /////////// Read Port
   axi4lite_mem.io.req.raddr := aligned_req_addri
   axi4lite_mem.io.req.wen := Mux(req_valid,req_fcn === M_XWR, false.B)
   axi4lite_mem.io.req.ren := Mux(req_valid,req_fcn === M_XRD, false.B)

   // req_typi may invalid when req_valid is false
   val resp_datai = axi4lite_mem.io.resp.bits.data
   val dport_addri = io.ports(DPORT).req.bits.addr
   val dport_typi = io.ports(DPORT).req.bits.typ
   val aligned_resp_data = resp_datai >> (dport_addri(1,0)<<3)
   when(state === s_lsu_active){
      resp_data := MuxCase(resp_datai,Seq(
         (dport_typi === MT_B) -> Cat(Fill(24,aligned_resp_data(7)),aligned_resp_data(7,0)),
         (dport_typi === MT_H) -> Cat(Fill(16,aligned_resp_data(15)),aligned_resp_data(15,0)),
         (dport_typi === MT_BU) -> Cat(Fill(24,0.U),aligned_resp_data(7,0)),
         (dport_typi === MT_HU) -> Cat(Fill(16,0.U),aligned_resp_data(15,0))
      ))
   } .otherwise{
      resp_data := resp_datai
   }
   

   /////////// Write Port
   when (req_valid && (req_fcn === M_XWR)){
      // axi4lite_mem.io.req.waddr := req_addri
      axi4lite_mem.io.req.data := req_data << (req_addri(1,0) << 3)
      axi4lite_mem.io.req.waddr := aligned_req_addri
      axi4lite_mem.io.req.mask := Mux(dport_typi === MT_B,1.U << req_addri(1,0),
                              Mux(dport_typi === MT_H,3.U << req_addri(1,0),15.U))
   }
}