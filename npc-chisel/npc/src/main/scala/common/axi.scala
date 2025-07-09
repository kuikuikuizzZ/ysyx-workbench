
package npc.common
{
import chisel3._
import chisel3.util._

import Constants._


class AXI4Req (val dataWidth : Int)(implicit val conf: YSYX24100012Config) extends Bundle{
    val addr = Input(conf.xprlen.W)
    val data = Input(dataWidth)
    val ren = Input (Bool())
    val wen = Input (Bool())
}

class AXI4Resp (val dataWidth : Int)(implicit val conf: YSYX24100012Config) extends Bundle{
    val data = Input(dataWidth)
}

class AXI4LiteAR (val addrWidth : Int) extends Bundle{
    val valid   =   Output(Bool())
    val addr    =   Output(addrWidth)
    val ready   =   Input(Bool()) 
} 

class AXI4LiteR (val addrWidth : Int) extends Bundle{
    val valid   =   Input(Bool())
    val data    =   Input(addrWidth)
    val ready   =   Output(Bool()) 
} 

class AXI4LiteAW (val addrWidth : Int) extends Bundle{
    val valid   =   Output(Bool())
    val addr    =   Output(addrWidth)
    val ready   =   Input(Bool()) 
} 

class AXI4LiteW (val addrWidth : Int) extends Bundle{
    val valid   =   Output(Bool())
    val data    =   Output(addrWidth)
    val ready   =   Input(Bool()) 
} 

class AXI4LiteB (val addrWidth : Int) extends Bundle{
    val valid   =   Input(Bool())
    val resp    =   Input(addrWidth)
    val ready   =   Output(Bool()) 
} 


class AXI4LiteMaster (implicit val conf: YSYX24100012Config) extends Module{
    io = IO( new Bundle {
        val clock     =   Input(Clock())
        val reset   =   Input(Bool())
        val ar      =   new AXI4LiteAR(conf.xprlen)
        val r       =   new AXI4LiteR(conf.xlen)
        val aw      =   new AXI4LiteAW(conf.xprlen)
        val w       =   new AXI4LiteW(conf.xlen)
        val b       =   new AXI4LiteB(conf.xlen)
        val req     =   new AXI4Req(conf.xlen)
        val resp    =   new DecoupledIO(new AXI4Resp())
    } 
    )
    io := DontCare
    //////  AXI4Lite read master
    val arvalid = RegInit(false.B)
    val araddr  = Reg(UInt(conf.xprlen.W))
    val arready = Wire(Bool())
    val rvalid  = Wire(Bool())
    // val rdata   = Reg(UInt(conf.xlen.W))
    val rready  = RegInit(true.B)

    val rs_idle :: rs_wait_arready :: rs_wait_rrvalid:: Nil = Enum(3)
    val rstate = RegInit(rs_idle)
        rstate := MuxLookup(state, rs_idle)(List(
        rs_idle       ->    Mux(io.req.ren, rs_wait_arready, rs_idle),
        rs_wait_arready ->  Mux(io.ar.ready, rs_wait_rrvalid, rs_wait_arready)
        rs_wait_rrvalid ->  Mux(io.r.valid, rs_idle, rs_wait_rrvalid)
    ))
    io.ar.valid := arvalid
    io.ar.addr = araddr
    io.r.ready = rready
    aready := io.ar.ready
    rvalid := io.r.valid
    
    switch(rstate){
        is(rs_idle){
            arvalid := true.B
            araddr  := io.req.addr
            io.resp.valid = false.B
        }
        is (rs_wait_arready){
            arvalid := false.B
            rready := true.B
        }
        is (rs_wait_rrvalid){
            rready := false.B
            io.resp.valid = true.B
            io.resp.data = io.r.data
        }
    }


    val awvalid = RegInit(false.B)
    // val awready = Wire(Bool())
    val wvalid  = RegInit(false.B)
    // val wready  = Wire(Bool())
    val bvalid  = Wire(Bool())
    val bready  = RegInit(true.B)
    val awaddr  = Reg(UInt(conf.xprlen.W))
    val wdata  = Reg(UInt(conf.xlen.W))

    val ws_idle :: ws_wait_rready :: rs_wait_rrvalid:: Nil = Enum(3)
    val wstate = RegInit(ws_idle)
        wstate := MuxLookup(state, ws_idle)(List(
        ws_idle         ->      Mux(io.req.wen, ws_wait_aready, ws_idle),
        ws_wait_ready   ->     Mux(io.aw.ready&&io.w.ready, ws_wait_wlast, ws_wait_ready),
        // ws_wait_wlast  ->   Mux(io.b.valid, ws_wait_bvalid, ws_wait_wlast)
        // AXI4lite burst length = 1
        ws_wait_wlast   ->      ws_wait_bvalid,
        ws_wait_bvalid  ->      Mux(io.b.valid, ws_idle, ws_wait_bvalid)
    ))

    //////  AXI4Lite write master
    io.aw.valid     :=  awvalid
    io.w.valid      :=  wvalid
    io.aw.addr      :=  awaddr
    io.w.data       :=  wdata
    io.b.ready      :=  bready
    switch(wstate){
        is(ws_idle){
            awvalid := true.B
            wvalid  := true.B
            awaddr  := io.req.addr
            wdata    := io.req.data
            io.resp.valid = false.B
        }
        is (ws_wait_aready){
            bready := true.B
        }
        is (ws_wait_wlast){
            awvalid := false.B
            wvalid := false.B
        }
        is (ws_wait_bvalid){
            bready := false.B
            io.resp.valid = true.B
            io.resp.data = io.b.resp
        }
    }
}

class AXI4LiteSlave (implicit val conf: YSYX24100012Config) extends Module{
    io = IO( new Bundle {
        val clock     =   Input(Clock())
        val reset   =   Input(Bool())
        val ar      =   new Flipped(new AXI4LiteAR(conf.xprlen))
        val r       =   new Flipped(new AXI4LiteR(conf.xlen))
        val aw      =   new Flipped(new AXI4LiteAW(conf.xprlen))
        val w       =   new Flipped(new AXI4LiteW(conf.xlen))
        val b       =   new Flipped(new AXI4LiteB(conf.xlen))
        val req     =   new DecoupledIO(new MemReq(conf.xlen))
        val resp    =   new DecoupledIO(new MemResp())
        } 
    )
    io := DontCare
    //////  AXI4Lite read slave
    val arready     =   RegInit(true.B)
    val araddr      =   Reg(UInt(conf.xprlen.W))
    val rvalid      =   RegInit(false.B)
    val rdata       =   Reg(UInt(conf.xlen.W))         
    val rready      =   Wire(Bool())
    val rs_wait_arvalid :: rs_prepare_data :: rs_wait_rready:: Nil = Enum(3)
    val rstate = RegInit(rs_wait_arvalid)
        rstate := MuxLookup(state, rs_wait_arvalid)(List(
        rs_wait_arvalid     ->      Mux(io.ar.valid, rs_prepare_data, rs_wait_arvalid),
        rs_prepare_data     ->      Mux(io.resp.ready, rs_wait_rready, rs_prepare_data)
        rs_wait_rready      ->      Mux(io.r.ready, rs_wait_arvalid, rs_wait_rready)
    ))
    io.ar.ready := arready
    araddr := io.ar.addr
    io.r.valid := rvalid
    io.r.data = rdata

    switch(rstate){
        is(rs_wait_arvalid){
            // when arvalid, data should valid 
            // reduce 1 cycle ?
            araddr := io.ar.addr
            io.req.addr := araddr
            // store for rlast support
        }
        is (rs_prepare_data){
            io.r.valid = true.B
            rvalid := true.B
            rdata := io.resp.data
        }
        is (rs_wait_rready){
            rvalid := false.B
        }
    }

    //////  AXI4Lite write slave
    val awready     := true.B
    val wready      := true.B
    val awaddr      := Reg(UInt(conf.xprlen))
    val bvalid      := RegInit(false.B)
    val bresp       := Reg(UInt(conf.xlen))
    
    val ws_wait_awvalid :: ws_wait_rready :: rs_wait_rrvalid:: Nil = Enum(3)
    val wstate = RegInit(ws_wait_awvalid)
        wstate := MuxLookup(state, ws_wait_awvalid)(List(
        ws_wait_awvalid     ->      Mux(io.aw.valid, ws_wait_wvalid, ws_wait_awvalid),
        ws_wait_wvalid      ->      Mux(io.w.valid , ws_wait_wlast, ws_wait_wlast),
        // ws_wait_wlast  ->   Mux(io.b.valid, ws_wait_bvalid, ws_wait_wlast)
        // AXI4lite burst length = 1
        ws_wait_wlast       ->      ws_wait_bready,
        ws_wait_bready      ->      Mux(io.b.ready, ws_wait_awvalid, ws_wait_bready)
    ))

    io.aw.valid     :=  awvalid
    io.w.valid      :=  wvalid
    io.aw.addr      :=  awaddr
    io.w.data       :=  wdata
    io.b.ready      :=  bready
    switch(wstate){
        is(ws_wait_awvalid){
            awaddr  := io.aw.addr
        }
        is (ws_wait_wvalid){
            io.req.addr     := awaddr
            // when wvalid high, io.req.data should valid
            io.req.data     := io.w.data
        }
        is (ws_wait_wlast){
            bvalid := true
            io.b.resp := bresp
        }
        is (ws_wait_bvalid){
            bvalid := false
        }
    }
}

}