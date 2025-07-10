package npc.common

import chisel3._
import chisel3.util._

import Constants._
import npc.common._


class AXI4Req (val dataWidth : Int)(implicit val conf: YSYX24100012Config) extends Bundle{
    val addr = Input(UInt(conf.xprlen.W))
    val data = Input(UInt(dataWidth.W))
    val ren = Input (Bool())
    val wen = Input (Bool())
}

class AXI4Resp(val data_width: Int) extends Bundle
{
   val data = Output(UInt(data_width.W))
}

class AXIWport(val addrWidth : Int,val dataWidth : Int) extends Bundle{
    val addr = Input(UInt(addrWidth.W))
    val data = Input(UInt(dataWidth.W))
    val en       =   Input(Bool())
    val ready    =   Output(Bool())
    val len = Input(UInt(dataWidth.W))
}

class AXIRport(val addrWidth : Int,val dataWidth : Int) extends Bundle{
    val addr     =   Input(UInt(addrWidth.W))
    val data     =   Output(UInt(dataWidth.W))
    val en       =   Input(Bool())
    val ready    =   Output(Bool())
}

class AXI4LiteAR (val addrWidth : Int) extends Bundle{
    val valid   =   Output(Bool())
    val addr    =   Output(UInt(addrWidth.W))
    val ready   =   Input(Bool()) 
} 

class AXI4LiteR (val addrWidth : Int) extends Bundle{
    val valid   =   Input(Bool())
    val data    =   Input(UInt(addrWidth.W))
    val ready   =   Output(Bool()) 
} 

class AXI4LiteAW (val addrWidth : Int) extends Bundle{
    val valid   =   Output(Bool())
    val addr    =   Output(UInt(addrWidth.W))
    val ready   =   Input(Bool()) 
} 

class AXI4LiteW (val addrWidth : Int) extends Bundle{
    val valid   =   Output(Bool())
    val data    =   Output(UInt(addrWidth.W))
    val ready   =   Input(Bool()) 
} 

class AXI4LiteB (val addrWidth : Int) extends Bundle{
    val valid   =   Input(Bool())
    val resp    =   Input(UInt(addrWidth.W))
    val ready   =   Output(Bool()) 
} 

class AXI4LiteIo (implicit val conf: YSYX24100012Config) extends Bundle{ 
    val ar      =   new AXI4LiteAR(conf.xprlen)
    val r       =   new AXI4LiteR(conf.xlen)
    val aw      =   new AXI4LiteAW(conf.xprlen)
    val w       =   new AXI4LiteW(conf.xlen)
    val b       =   new AXI4LiteB(conf.xlen)
}

class AXI4LiteMaster (implicit val conf: YSYX24100012Config) extends Module{
    val io = IO( new Bundle {
        val clock   =   Input(Clock())
        val reset   =   Input(Bool())
        val axi_io  =   new AXI4LiteIo()
        val req     =   new AXI4Req(conf.xlen)
        val resp    =   new DecoupledIO(new AXI4Resp(conf.xlen))
    })
    io := DontCare
    io.resp.valid := false.B
    io.resp.bits := DontCare

    //////  AXI4Lite write/read channel
    val arvalid = RegInit(false.B)
    val araddr  = Reg(UInt(conf.xprlen.W))
    val arready = Wire(Bool())
    val rvalid  = Wire(Bool())
    // val rdata   = Reg(UInt(conf.xlen.W))
    val rready  = RegInit(true.B)
    val awvalid = RegInit(false.B)
    // val awready = Wire(Bool())
    val wvalid  = RegInit(false.B)
    // val bvalid  = Wire(Bool())
    val bready  = RegInit(true.B)
    val awaddr  = Reg(UInt(conf.xprlen.W))
    val wdata  = Reg(UInt(conf.xlen.W))

    
    when (io.req.wen){ 
        awvalid         := true.B
        wvalid          := true.B
        arvalid         := false.B
        awaddr          := io.req.addr
        wdata           := io.req.data
        io.resp.valid   := io.axi_io.b.valid
    }  .otherwise {
        wvalid          := false.B
        awvalid         := false.B
        araddr          := io.req.addr
        arvalid         := true.B
        wdata           :=    0.U
        io.resp.valid   := io.axi_io.r.valid
    }


    //////  AXI4Lite read master
    val rs_idle :: rs_wait_arready :: rs_wait_rrvalid :: Nil = Enum(3)
    val rstate = RegInit(rs_idle)
        rstate := MuxLookup(rstate, rs_idle)(List(
        rs_idle       ->    Mux(io.req.ren, rs_wait_arready, rs_idle),
        rs_wait_arready ->  Mux(io.axi_io.ar.ready, rs_wait_rrvalid, rs_wait_arready),
        rs_wait_rrvalid ->  Mux(io.axi_io.r.valid, rs_idle, rs_wait_rrvalid)
    ))
    io.axi_io.ar.valid  := arvalid
    io.axi_io.ar.addr   := araddr
    io.axi_io.r.ready   := rready
    arready             := io.axi_io.ar.ready
    rvalid              := io.axi_io.r.valid

    switch(rstate){
        is(rs_idle){

        }
        is (rs_wait_arready){
            rready := true.B
        }
        is (rs_wait_rrvalid){
            arvalid := false.B
            // rready          := false.B
            when (io.axi_io.r.valid){
                io.resp.bits.data    := io.axi_io.r.data
            }
        }
    }




    val ws_idle :: ws_wait_ready :: ws_wait_wlast::ws_wait_bvalid:: Nil = Enum(4)
    val wstate = RegInit(ws_idle)
        wstate := MuxLookup(wstate, ws_idle)(List(
        ws_idle         ->      Mux(io.req.wen, ws_wait_ready, ws_idle),
        ws_wait_ready   ->     Mux(io.axi_io.aw.ready&&io.axi_io.w.ready, ws_wait_wlast, ws_wait_ready),
        // ws_wait_wlast  ->   Mux(io.axi_io.b.valid, ws_wait_bvalid, ws_wait_wlast)
        // AXI4lite burst length = 1
        ws_wait_wlast   ->      ws_wait_bvalid,
        ws_wait_bvalid  ->      Mux(io.axi_io.b.valid, ws_idle, ws_wait_bvalid)
    ))

    //////  AXI4Lite write master
    io.axi_io.aw.valid     :=  awvalid
    io.axi_io.w.valid      :=  wvalid
    io.axi_io.aw.addr      :=  awaddr
    io.axi_io.w.data       :=  wdata
    io.axi_io.b.ready      :=  bready
    switch(wstate){
        is(ws_idle){
                
        }
        is (ws_wait_ready){
            bready := true.B
        }
        is (ws_wait_wlast){
            awvalid := false.B
            wvalid := false.B
        }
        is (ws_wait_bvalid){
            bready          := false.B
            io.resp.bits.data    := io.axi_io.b.resp
        }
    }
}

class AXI4LiteSlave (implicit val conf: YSYX24100012Config) extends Module{
    val io = IO( new Bundle {
        val clock   =   Input(Clock())
        val reset   =   Input(Bool())
        val axi_io  =   Flipped(new AXI4LiteIo())
    })
    io := DontCare

    val slave_mem = Module(new YSYX2400012AXI4LiteMem())
    slave_mem.io := DontCare
    slave_mem.io.clock := clock
    slave_mem.io.reset := reset

    //////  AXI4Lite read slave
    val arready     =   true.B
    val araddr      =   Reg(UInt(conf.xprlen.W))
    val rvalid      =   RegInit(false.B)
    val rdata       =   Reg(UInt(conf.xlen.W))   
    // val rready      =   Wire(Bool())
    val rs_wait_arvalid :: rs_prepare_data :: rs_wait_rready:: Nil = Enum(3)
    val rstate = RegInit(rs_wait_arvalid)
        rstate := MuxLookup(rstate, rs_wait_arvalid)(List(
        rs_wait_arvalid     ->      Mux(io.axi_io.ar.valid, rs_prepare_data, rs_wait_arvalid),
        rs_prepare_data     ->      Mux(slave_mem.io.dr.ready, rs_wait_rready, rs_prepare_data),
        rs_wait_rready      ->      Mux(io.axi_io.r.ready, rs_wait_arvalid, rs_wait_rready)
    ))
    slave_mem.io.dr := DontCare
    io.axi_io.ar.ready  := arready
    // araddr              := io.axi_io.ar.addr
    io.axi_io.r.valid   := rvalid
    io.axi_io.r.data    := rdata
    val req_addri = araddr
    // slave_mem.io.dr.valid := io.axi_io.ar.valid
    slave_mem.io.dr.addr := req_addri
    rdata := slave_mem.io.dr.data

    switch(rstate){
        is(rs_wait_arvalid){
            // when arvalid, data should valid 
            // reduce 1 cycle ?
            araddr := io.axi_io.ar.addr
            // store for rlast support
        }
        is (rs_prepare_data){
            // syncmem 1 cycle latency
            slave_mem.io.dr.en := true.B
            rvalid := true.B
        }
        is (rs_wait_rready){
            io.axi_io.r.data := rdata
            rvalid := false.B
            // slave_mem.io.dr.en := false.B
        }
    }

    //////  AXI4Lite write slave
    val awready     = true.B
    val wready      = true.B
    val awaddr      = Reg(UInt(conf.xprlen.W))
    val wdata       = Reg(UInt(conf.xlen.W))
    val bvalid      = RegInit(false.B)
    val bresp       = Reg(UInt(conf.xlen.W))
    
    val ws_wait_awvalid :: ws_wait_wvalid :: ws_wait_wlast:: ws_wait_bready:: Nil = Enum(4)
    val wstate = RegInit(ws_wait_awvalid)
        wstate := MuxLookup(wstate, ws_wait_awvalid)(List(
        ws_wait_awvalid     ->      Mux(io.axi_io.aw.valid, ws_wait_wvalid, ws_wait_awvalid),
        ws_wait_wvalid      ->      Mux(io.axi_io.w.valid , ws_wait_wlast, ws_wait_wlast),
        // ws_wait_wlast  ->   Mux(io.axi_io.b.valid, ws_wait_bvalid, ws_wait_wlast)
        // AXI4lite burst length = 1
        ws_wait_wlast       ->      ws_wait_bready,
        ws_wait_bready      ->      Mux(io.axi_io.b.ready, ws_wait_awvalid, ws_wait_bready)
    ))

    // io.axi_io.aw.valid     :=  awvalid
    // io.axi_io.w.valid      :=  wvalid
    io.axi_io.aw.ready      :=  awready
    io.axi_io.w.ready       :=  wready
    awaddr                  :=  io.axi_io.aw.addr
    wdata                   := io.axi_io.w.data        
    // io.axi_io.b.ready       :=  bready
    
    switch(wstate){
        is(ws_wait_awvalid){
            awaddr  := io.axi_io.aw.addr
        }
        is (ws_wait_wvalid){
            slave_mem.io.dw.addr     := awaddr
            // when wvalid high, slave_mem.io.dw.data should valid
            slave_mem.io.dw.data    := io.axi_io.w.data
            slave_mem.io.dw.en      := io.axi_io.w.valid
            slave_mem.io.dw.len     := 4.U
        }
        is (ws_wait_wlast){
            bvalid := true.B
            io.axi_io.b.resp := bresp
        }
        is (ws_wait_bready){
            bvalid := false.B
        }
    }
}
