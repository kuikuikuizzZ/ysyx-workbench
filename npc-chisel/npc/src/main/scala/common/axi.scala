package npc.common

import chisel3._
import chisel3.util._

import Constants._
import npc.common._


class AXI4Req (val dataWidth : Int)(implicit val conf: YSYX24100012Config) extends Bundle{
    val maskWidth = dataWidth/8
    val raddr = Input(UInt(conf.xprlen.W))
    val waddr = Input(UInt(conf.xprlen.W))
    val data = Input(UInt(dataWidth.W))
    val mask = Input(UInt(maskWidth.W))
    val ren = Input (Bool())
    val wen = Input (Bool())
}

class AXI4Resp(val data_width: Int) extends Bundle
{
   val data = Output(UInt(data_width.W))
   val resp = Output(UInt(2.W))
}

class AXIWport(val addrWidth : Int,val dataWidth : Int) extends Bundle{
    val maskWidth = addrWidth/8
    val addr = Input(UInt(addrWidth.W))
    val data = Input(UInt(dataWidth.W))
    val en       =   Input(Bool())
    val ready    =   Output(Bool())
    val mask = Input(UInt(maskWidth.W))
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
    val resp    =   Input(UInt(2.W))
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
    val strb    =   Output(UInt(addrWidth.W))
    val ready   =   Input(Bool()) 
} 

class AXI4LiteB (val addrWidth : Int) extends Bundle{
    val valid   =   Input(Bool())
    val resp    =   Input(UInt(2.W))
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
    io.resp.bits := DontCare

    //////  AXI4Lite read/write master
    val rs_idle :: rs_wait_arready :: rs_wait_rvalid :: Nil = Enum(3)
    val rstate = RegInit(rs_idle)
    val ws_idle :: ws_wait_ready ::ws_wait_bvalid:: Nil = Enum(3)
    val wstate = RegInit(ws_idle)

    val accept_read = (rstate === rs_idle) && io.req.ren
    val accept_write = !accept_read && (wstate === ws_idle) && io.req.wen
    val is_read = Mux((rstate === rs_idle), accept_read, RegEnable (accept_read,(rstate === rs_idle)))
    val is_write = Mux((wstate === ws_idle), accept_write, RegEnable (accept_write,(wstate === ws_idle)))

    //////  AXI4Lite write/read channel
    // RegNext 2 cycle, maybe need to change
    val maskWidth   = conf.xlen/8
    val arvalid = Reg(Bool())
    val araddr  = Reg(UInt(conf.xprlen.W))
    val awvalid = Reg(Bool())
    val awaddr  = Reg(UInt(conf.xprlen.W))
    val wvalid  = Reg(Bool())
    val wdata  =  Reg(UInt(conf.xlen.W))
    val wstrb   = Reg(UInt(maskWidth.W))

    arvalid     := (accept_read)
    araddr      := (io.req.raddr)
    awvalid     := (accept_write)
    awaddr      := (io.req.waddr)
    wvalid      := (accept_write)
    wdata       := (io.req.data)
    wstrb       := (io.req.mask)


    val rready  = !is_write
    val bready  = is_write

    io.axi_io.ar.valid  := Mux(rstate===rs_idle, accept_read,arvalid)
    io.axi_io.ar.addr   := Mux(rstate===rs_idle, io.req.raddr,araddr)
    io.axi_io.r.ready   := rready

    switch(rstate){
        is(rs_idle)         { rstate := Mux(io.req.ren, rs_wait_arready, rs_idle)}
        is (rs_wait_arready){ 
            rstate := Mux(io.axi_io.ar.ready, rs_wait_rvalid, rs_wait_arready)
            when(io.axi_io.ar.ready){  arvalid := false.B   }}
        is (rs_wait_rvalid){ 
            rstate := Mux(io.axi_io.r.valid, rs_idle, rs_wait_rvalid)
            when (io.axi_io.r.valid){ io.resp.bits.data  := io.axi_io.r.data}
        }
    }

    
    //////  AXI4Lite write master
    io.axi_io.aw.valid     :=  Mux(wstate===ws_idle, accept_write,awvalid)
    io.axi_io.w.valid      :=  Mux(wstate===ws_idle, accept_write,wvalid)
    io.axi_io.aw.addr      :=  awaddr
    io.axi_io.w.data       :=  wdata
    io.axi_io.w.strb       :=  wstrb
    io.axi_io.b.ready      :=  bready
    switch(wstate){
        is(ws_idle)         { wstate := Mux(io.req.wen, ws_wait_ready, ws_idle)}
        is (ws_wait_ready)  { 
            wstate := Mux(io.axi_io.aw.ready&&io.axi_io.w.ready, ws_wait_bvalid, ws_wait_ready)
            when(io.axi_io.aw.ready){ awvalid := false.B}
            when(io.axi_io.w.ready) {  wvalid := false.B}}
        is (ws_wait_bvalid) { wstate := Mux(io.axi_io.b.valid, ws_idle, ws_wait_bvalid)}
    }
    // io.resp.valid := Mux(is_write ,(wstate === ws_wait_bvalid)&&(io.axi_io.b.resp === 0.U) ,
    //                  (rstate === rs_wait_rvalid)&&(io.axi_io.r.resp === 0.U) )
    io.resp.valid := Mux(is_write ,(wstate === ws_wait_bvalid)&&(io.axi_io.b.valid) ,
                     (rstate === rs_wait_rvalid)&&(io.axi_io.r.valid) )
}

class AXI4LiteSlave (implicit val conf: YSYX24100012Config) extends Module{
    val io = IO( new Bundle {
        val clock   =   Input(Clock())
        val reset   =   Input(Bool())
        val axi_io  =   Flipped(new AXI4LiteIo())
    })
    io := DontCare

    val s_idle :: s_inflight :: s_wait_rready_bready :: Nil = Enum(3)
    val state = RegInit(s_idle)
    val accept_read = (state === s_idle) && io.axi_io.ar.valid
    val accept_write = !accept_read && (state === s_idle) && io.axi_io.aw.valid && io.axi_io.w.valid
    val is_write = Mux((state === s_idle), accept_write, RegEnable(accept_write,(state === s_idle)))
    val slave_mem = Module(new YSYX2400012AXI4LiteMem())
    slave_mem.io := DontCare
    slave_mem.io.clock := clock
    slave_mem.io.reset := reset
    slave_mem.io.dr := DontCare

    switch (state) {
        is (s_idle)     { state := Mux(io.axi_io.ar.valid || (io.axi_io.aw.valid && io.axi_io.w.valid), s_inflight, s_idle) }
        is (s_inflight) { state := Mux((!is_write &&slave_mem.io.dr.ready) || (is_write && slave_mem.io.dw.ready) ,  s_wait_rready_bready, s_inflight) }
        // is (s_inflight) { state := Mux(slave_mem.io.dr.ready ,  s_wait_rready_bready, s_inflight) }
        is (s_wait_rready_bready) { state := Mux(io.axi_io.r.ready || io.axi_io.b.ready , s_idle, s_wait_rready_bready) }
    }

    //////  AXI4Lite 
    io.axi_io.ar.ready      :=   accept_read  || state === s_inflight
    io.axi_io.w.ready       :=   accept_write || state === s_inflight
    io.axi_io.aw.ready      :=   accept_write || state === s_inflight
    val araddr              =   Mux(accept_read, io.axi_io.ar.addr,     RegEnable(io.axi_io.ar.addr,io.axi_io.ar.valid))
    val awaddr              =   Mux(accept_write, io.axi_io.aw.addr,    RegEnable(io.axi_io.aw.addr,io.axi_io.aw.valid))
    val wdata               =   Mux(accept_write, io.axi_io.w.data,     RegEnable(io.axi_io.w.data,io.axi_io.w.valid))
    val wstrb               =   Mux(accept_write, io.axi_io.w.strb,     RegEnable(io.axi_io.w.strb,io.axi_io.w.valid)) 

    slave_mem.io.dr.en      := Mux(io.axi_io.ar.valid,true.B,false.B)
    slave_mem.io.dr.addr    := araddr
    slave_mem.io.dw.en      := Mux(io.axi_io.aw.valid,true.B,false.B)
    slave_mem.io.dw.addr    := awaddr
    // when wvalid high, slave_mem.io.dw.data should valid
    slave_mem.io.dw.data    := wdata
    slave_mem.io.dw.mask    := wstrb


    val resp        =   0.U  // OKAY
    val resp_hold = Mux((state === s_inflight),resp, RegNext(resp))  

    io.axi_io.r.resp    := resp_hold
    io.axi_io.r.valid   := !is_write && (state === s_inflight && slave_mem.io.dr.ready) || (state === s_wait_rready_bready)
    io.axi_io.r.data    := Mux((state === s_inflight),slave_mem.io.dr.data, RegEnable(slave_mem.io.dr.data,(state === s_inflight)))  

    io.axi_io.b.valid   := is_write && (((state === s_inflight) && slave_mem.io.dw.ready ) || (state === s_wait_rready_bready))
    io.axi_io.b.resp    := resp_hold
}


class AXI4LiteSlaveData (implicit val conf: YSYX24100012Config) extends Module{
    val io = IO( new Bundle {
        val clock   =   Input(Clock())
        val reset   =   Input(Bool())
        val axi_io  =   Flipped(new AXI4LiteIo())
    })
    io := DontCare

    val s_idle :: s_inflight :: s_wait_rready_bready :: Nil = Enum(3)
    val state = RegInit(s_idle)
    val accept_read = (state === s_idle) && io.axi_io.ar.valid
    val accept_write = !accept_read && (state === s_idle) && io.axi_io.aw.valid && io.axi_io.w.valid
    val is_write = Mux((state === s_idle), accept_write, RegEnable(accept_write,(state === s_idle)))
    val slave_mem = Module(new YSYX2400012AXI4LiteMem())
    slave_mem.io := DontCare
    slave_mem.io.clock := clock
    slave_mem.io.reset := reset
    slave_mem.io.dr := DontCare

    switch (state) {
        is (s_idle)     { state := Mux(io.axi_io.ar.valid || (io.axi_io.aw.valid && io.axi_io.w.valid), s_inflight, s_idle) }
        is (s_inflight) { state := Mux((!is_write &&slave_mem.io.dr.ready) || (is_write && slave_mem.io.dw.ready) ,  s_wait_rready_bready, s_inflight) }
        // is (s_inflight) { state := Mux(slave_mem.io.dr.ready ,  s_wait_rready_bready, s_inflight) }
        is (s_wait_rready_bready) { state := Mux(io.axi_io.r.ready || io.axi_io.b.ready , s_idle, s_wait_rready_bready) }
    }

    //////  AXI4Lite 
    io.axi_io.ar.ready      :=   accept_read  || state === s_inflight
    io.axi_io.w.ready       :=   accept_write || state === s_inflight
    io.axi_io.aw.ready      :=   accept_write || state === s_inflight
    val araddr              =   Mux(accept_read, io.axi_io.ar.addr, RegEnable(io.axi_io.ar.addr,accept_read))
    val awaddr              =   Mux(is_write, io.axi_io.aw.addr,    RegEnable(io.axi_io.aw.addr,is_write))
    val wdata               =   Mux(is_write, io.axi_io.w.data,     RegEnable(io.axi_io.w.data,is_write))
    val wstrb               =   Mux(is_write, io.axi_io.w.strb,     RegEnable(io.axi_io.w.strb,is_write)) 

    slave_mem.io.dr.en      := Mux(io.axi_io.ar.valid,true.B,false.B)
    slave_mem.io.dr.addr    := araddr
    slave_mem.io.dw.en      := Mux(io.axi_io.aw.valid,true.B,false.B)
    slave_mem.io.dw.addr    := awaddr
    // when wvalid high, slave_mem.io.dw.data should valid
    slave_mem.io.dw.data    := wdata
    slave_mem.io.dw.mask    := wstrb


    val resp        =   0.U  // OKAY
    val resp_hold = Mux((state === s_inflight),resp, RegNext(resp))  

    io.axi_io.r.resp    := resp_hold
    io.axi_io.r.valid   := !is_write && (state === s_inflight && slave_mem.io.dr.ready) || (state === s_wait_rready_bready)
    io.axi_io.r.data    := Mux((state === s_inflight),slave_mem.io.dr.data, RegEnable(slave_mem.io.dr.data,(state === s_inflight)))  

    io.axi_io.b.valid   := is_write && (((state === s_inflight) && slave_mem.io.dw.ready ) || (state === s_wait_rready_bready))
    io.axi_io.b.resp    := resp_hold
}

class AXI4LiteMasterData (implicit val conf: YSYX24100012Config) extends Module{
    val io = IO( new Bundle {
        val clock   =   Input(Clock())
        val reset   =   Input(Bool())
        val axi_io  =   new AXI4LiteIo()
        val req     =   new AXI4Req(conf.xlen)
        val resp    =   new DecoupledIO(new AXI4Resp(conf.xlen))
    })
    io := DontCare
    io.resp.bits := DontCare

    //////  AXI4Lite read/write master
    val rs_idle :: rs_wait_arready :: rs_wait_rvalid :: Nil = Enum(3)
    val rstate = RegInit(rs_idle)
    val ws_idle :: ws_wait_ready ::ws_wait_bvalid:: Nil = Enum(3)
    val wstate = RegInit(ws_idle)
    val accept_read = (rstate === rs_idle) && io.req.ren
    val accept_write = !accept_read && (wstate === ws_idle) && io.req.wen
    val is_read = Mux((rstate === rs_idle), accept_read, RegEnable (accept_read,(rstate === rs_idle)))
    val is_write = Mux((wstate === ws_idle), accept_write, RegEnable (accept_write,(wstate === ws_idle)))

    //////  AXI4Lite write/read channel
    // RegNext 2 cycle, maybe need to change
    val maskWidth   = conf.xlen/8
    val arvalid = Reg(Bool())
    val araddr  = Reg(UInt(conf.xprlen.W))
    val awvalid = Reg(Bool())
    val awaddr  = Reg(UInt(conf.xprlen.W))
    val wvalid  = Reg(Bool())
    val wdata  =  Reg(UInt(conf.xlen.W))
    val wstrb   = Reg(UInt(maskWidth.W))

    arvalid     := (accept_read)
    araddr      := (io.req.raddr)
    awvalid     := (accept_write)
    awaddr      := (io.req.waddr)
    wvalid      := (accept_write)
    wdata       := (io.req.data)
    wstrb       := (io.req.mask)


    val rready  = !is_write
    val bready  = is_write

    io.axi_io.ar.valid  := Mux(rstate===rs_idle, accept_read,arvalid)
    io.axi_io.ar.addr   := Mux(rstate===rs_idle, io.req.raddr,araddr)
    io.axi_io.r.ready   := rready

    switch(rstate){
        is(rs_idle)         { rstate := Mux(io.req.ren, rs_wait_arready, rs_idle)}
        is (rs_wait_arready){ 
            rstate := Mux(io.axi_io.ar.ready, rs_wait_rvalid, rs_wait_arready)
            when(io.axi_io.ar.ready){  arvalid := false.B   }}
        is (rs_wait_rvalid){ 
            rstate := Mux(io.axi_io.r.valid, rs_idle, rs_wait_rvalid)
            when (io.axi_io.r.valid){ io.resp.bits.data  := io.axi_io.r.data}
        }
    }

    
    //////  AXI4Lite write master
    io.axi_io.aw.valid     :=  Mux(wstate===ws_idle, accept_write,awvalid)
    io.axi_io.w.valid      :=  Mux(wstate===ws_idle, accept_write,wvalid)
    io.axi_io.aw.addr      :=  awaddr
    io.axi_io.w.data       :=  wdata
    io.axi_io.w.strb       :=  wstrb
    io.axi_io.b.ready      :=  bready
    switch(wstate){
        is(ws_idle)         { wstate := Mux(io.req.wen, ws_wait_ready, ws_idle)}
        is (ws_wait_ready)  { 
            wstate := Mux(io.axi_io.aw.ready&&io.axi_io.w.ready, ws_wait_bvalid, ws_wait_ready)
            when(io.axi_io.aw.ready){ awvalid := false.B}
            when(io.axi_io.w.ready) {  wvalid := false.B}}
        is (ws_wait_bvalid) { wstate := Mux(io.axi_io.b.valid, ws_idle, ws_wait_bvalid)}
    }
    // io.resp.valid := Mux(is_write ,(wstate === ws_wait_bvalid)&&(io.axi_io.b.resp === 0.U) ,
    //                  (rstate === rs_wait_rvalid)&&(io.axi_io.r.resp === 0.U) )
    io.resp.valid := Mux(is_write ,(wstate === ws_wait_bvalid)&&(io.axi_io.b.valid) ,
                     (rstate === rs_wait_rvalid)&&(io.axi_io.r.valid) )
}