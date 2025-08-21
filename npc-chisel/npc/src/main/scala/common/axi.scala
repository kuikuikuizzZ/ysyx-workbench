package npc.common

import chisel3._
import chisel3.util._

import Constants._
import npc.common._
import npc.devices.{ysyx_24100012_AXI4LiteMem,ysyx_24100012_AXI4LiteMemRandomDelay}

class AXI4Req (val dataWidth : Int)(implicit val conf: ysyx_24100012_Config) extends Bundle{
    val maskWidth = dataWidth/8
    val raddr   = Input(UInt(conf.xprlen.W))
    val waddr   = Input(UInt(conf.xprlen.W))
    val data    = Input(UInt(dataWidth.W))
    val mask    = Input(UInt(maskWidth.W))
    val ren     = Input (Bool())
    val wen     = Input (Bool())
    val burst   = Input(Bool())
    val burstlen = Input(UInt(conf.AXIBurstLenBits.W))
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


class AXI4LiteAR (val addrWidth : Int) (implicit val conf: ysyx_24100012_Config) extends Bundle{
    val valid   =   Output(Bool())
    val addr    =   Output(UInt(conf.xprlen.W))
    val ready   =   Input(Bool()) 
    val id      =   Output(UInt(conf.idBits.W))
    val len     =   Output(UInt(conf.lenBits.W))  // number of beats - 1
    val size    =   Output(UInt(conf.sizeBits.W)) // bytes in beat = 2^size
    val burst   =   Output(UInt(conf.burstBits.W))
} 

class AXI4LiteR (val addrWidth : Int) (implicit val conf: ysyx_24100012_Config)extends Bundle{
    val valid   =   Input(Bool())
    val data    =   Input(UInt(addrWidth.W))
    val resp    =   Input(UInt(2.W))
    val ready   =   Output(Bool()) 
    val id      =   Input(UInt(conf.idBits.W))
    val last    =   Input(Bool())
} 

class AXI4LiteAW (val addrWidth : Int) (implicit val conf: ysyx_24100012_Config) extends Bundle{
    val valid   =   Output(Bool())
    val addr    =   Output(UInt(addrWidth.W))
    val ready   =   Input(Bool()) 
    val id      =   Output(UInt(conf.idBits.W))
    val len     =   Output(UInt(conf.lenBits.W))  // number of beats - 1
    val size    =   Output(UInt(conf.sizeBits.W)) // bytes in beat = 2^size
    val burst   =   Output(UInt(conf.burstBits.W))
} 

class AXI4LiteW (val addrWidth : Int) (implicit val conf: ysyx_24100012_Config) extends Bundle{
    val valid   =   Output(Bool())
    val data    =   Output(UInt(addrWidth.W))
    val strb    =   Output(UInt(conf.maskBits.W))
    val ready   =   Input(Bool()) 
    val last    =   Output(Bool())
} 

class AXI4LiteB (val addrWidth : Int) (implicit val conf: ysyx_24100012_Config) extends Bundle{
    val valid   =   Input(Bool())
    val resp    =   Input(UInt(2.W))
    val ready   =   Output(Bool()) 
    val id      =   Input(UInt(conf.idBits.W))
} 

class AXI4LiteIo (implicit val conf: ysyx_24100012_Config) extends Bundle{ 
    val ar      =   new AXI4LiteAR(conf.xprlen)
    val r       =   new AXI4LiteR(conf.xlen)
    val aw      =   new AXI4LiteAW(conf.xprlen)
    val w       =   new AXI4LiteW(conf.xlen)
    val b       =   new AXI4LiteB(conf.xlen)
}

class ysyx_24100012_AXI4LiteMaster (implicit val conf: ysyx_24100012_Config) extends Module{
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
    val rs_idle :: rs_wait_arready :: rs_wait_rlast :: Nil = Enum(3)
    val rstate = RegInit(rs_idle)
    val ws_idle :: ws_wait_ready ::ws_wait_bvalid:: Nil = Enum(3)
    val wstate = RegInit(ws_idle)

    val accept_read = (rstate === rs_idle) && io.req.ren
    val accept_write = !accept_read && (wstate === ws_idle) && io.req.wen
    val is_read = Mux((rstate === rs_idle), accept_read, RegEnable (accept_read,false.B,(rstate === rs_idle)))
    val is_write = Mux((wstate === ws_idle), accept_write, RegEnable (accept_write,false.B,(wstate === ws_idle)))
    val awfire = RegInit(false.B)
    val wfire = RegInit(false.B)
    val arfire = RegInit(false.B)
    val bfire = RegInit(false.B)
    val rfire = RegInit(false.B)

    awfire  :=  Mux(wstate === ws_wait_bvalid,  false.B, (io.axi_io.aw.valid && io.axi_io.aw.ready) || awfire)
    wfire   :=  Mux(wstate === ws_wait_bvalid,  false.B, (io.axi_io.w.valid && io.axi_io.w.ready) || wfire)
    bfire   :=  Mux(wstate === ws_idle,         false.B,        (io.axi_io.b.valid && io.axi_io.b.ready) || bfire)
    arfire  :=  Mux(rstate === rs_wait_rlast || io.axi_io.r.last,   false.B,  (io.axi_io.ar.valid && io.axi_io.ar.ready) || arfire)
    rfire   :=  Mux(rstate === rs_idle,         false.B,  (io.axi_io.r.valid && io.axi_io.r.ready) || rfire)


    //////  AXI4Lite write/read channel
    // RegNext 2 cycle, maybe need to change
    val maskWidth   = conf.xlen/8
    val arvalid = Mux(arfire || rstate===rs_wait_rlast,false.B, is_read)
    val araddr  = Mux(accept_read,io.req.raddr,RegEnable(io.req.raddr,  0.U ,  accept_read||io.axi_io.ar.ready))
    val arlen   = Mux(io.req.burst,io.req.burstlen,0.U)
    val arburst = io.req.burst

    val awaddr  =   Mux(accept_write,io.req.waddr,RegEnable(io.req.waddr, accept_write||io.axi_io.aw.ready))
    val awvalid =   Mux(awfire || awfire || wstate === ws_wait_bvalid,false.B, is_write)
    val wvalid  =   Mux(wfire || wstate === ws_wait_bvalid,false.B, is_write)
    val wlast   =   Mux(wfire || wstate === ws_wait_bvalid,false.B, is_write)
    val wdata   =   Mux(accept_write,io.req.data,RegEnable(io.req.data, 0.U,  accept_write||io.axi_io.w.ready))
    val wstrb   =   Mux(accept_write,io.req.mask,RegEnable(io.req.mask, 0.U,  accept_write||io.axi_io.w.ready))
    val awlen   =   Mux(io.req.burst,io.req.burstlen,0.U)
    val awburst =   io.req.burst

    val rready  = (rstate === rs_wait_rlast) || (rstate === rs_wait_arready ) 
    val bready  = (wstate === ws_wait_bvalid) || (wstate === ws_wait_ready ) 
    
    io.axi_io.ar.addr   := Mux(rstate===rs_idle, io.req.raddr,araddr)
    io.axi_io.ar.valid  := arvalid
    io.axi_io.ar.burst  := arburst   
    io.axi_io.ar.len    := arlen
    io.axi_io.r.ready   := rready
    io.axi_io.b.ready   := bready
    switch(rstate){
        is(rs_idle)         { rstate := Mux(accept_read, Mux(io.axi_io.ar.valid && io.axi_io.ar.ready,rs_wait_rlast , rs_wait_arready), rs_idle)}
        is (rs_wait_arready){ rstate := Mux(arfire, rs_wait_rlast, rs_wait_arready)}
        is (rs_wait_rlast){ 
            // rlast is high when rvalid is high
            // rstate := Mux(io.axi_io.r.last || (rstate === rs_wait_rlast) && (rfire), rs_idle, rs_wait_rlast)
            rstate := Mux(io.axi_io.r.last , rs_idle, rs_wait_rlast)
            when (io.axi_io.r.valid){ io.resp.bits.data  := io.axi_io.r.data}
        }
    }

    
    //////  AXI4Lite write master
    io.axi_io.aw.valid      :=  Mux(wstate===ws_idle, accept_write,awvalid)
    io.axi_io.w.valid       :=  Mux(wstate===ws_idle, accept_write,wvalid)
    io.axi_io.aw.addr       :=  awaddr
    io.axi_io.w.data        :=  wdata
    io.axi_io.w.strb        :=  wstrb
    io.axi_io.w.last        :=  wlast
    // write not support burst 
    // io.axi_io.aw.burst      :=  awburst
    io.axi_io.aw.len        :=  0.U
    
    switch(wstate){
        is(ws_idle)         { wstate := Mux(accept_write, ws_wait_ready, ws_idle)}
        is (ws_wait_ready)  { wstate := Mux((awfire&&wfire), ws_wait_bvalid, ws_wait_ready)}
        is (ws_wait_bvalid) { wstate := Mux(bfire , ws_idle, ws_wait_bvalid)}
    }

    io.resp.valid := Mux(is_write ,(wstate === ws_wait_bvalid)&&(bfire) , (io.axi_io.r.valid)  )
    io.resp.bits.resp :=  Mux(is_write ,io.axi_io.b.resp, io.axi_io.r.resp )
}



class ysyx_24100012_AXI4LiteSlave (implicit val conf: ysyx_24100012_Config) extends Module{
    val io = IO( new Bundle {
        val clock   =   Input(Clock())
        val reset   =   Input(Bool())
        val axi_io  =   Flipped(new AXI4LiteIo())
        val out     =   Flipped(new Bundle{
            val dr      =   new AXIRport(conf.xprlen, conf.xlen)
            val dw      =   new AXIWport(conf.xprlen, conf.xlen)
        })
        val debug =   new Bundle{
            val state = Output(UInt(2.W))
        }
    })
    io := DontCare

    val s_idle :: s_inflight :: s_wait_rready_bready :: Nil = Enum(3)
    val state = RegInit(s_idle)
    val accept_read = (state === s_idle) && io.axi_io.ar.valid
    val accept_write = !accept_read && (state === s_idle) && io.axi_io.aw.valid && io.axi_io.w.valid
    val is_write = Mux((state === s_idle), accept_write, RegEnable(accept_write,false.B,(state === s_idle)))
    io.debug.state := state

    switch (state) {
        is (s_idle)     { state := Mux(io.axi_io.ar.valid || (io.axi_io.aw.valid && io.axi_io.w.valid), s_inflight, s_idle) }
        is (s_inflight) { state := Mux((!is_write &&io.out.dr.ready) || (is_write && io.out.dw.ready) ,  s_wait_rready_bready, s_inflight) }
        // is (s_inflight) { state := Mux(io.out.dr.ready ,  s_wait_rready_bready, s_inflight) }
        is (s_wait_rready_bready) { state := Mux(io.axi_io.r.ready || io.axi_io.b.ready , s_idle, s_wait_rready_bready) }
    }
    //////  AXI4Lite 
    io.axi_io.ar.ready      :=   accept_read  || (state === s_inflight && !is_write)
    io.axi_io.w.ready       :=   accept_write || (state === s_inflight && is_write)
    io.axi_io.aw.ready      :=   accept_write || (state === s_inflight && is_write)
    val araddr              =   Mux(accept_read, io.axi_io.ar.addr,         RegEnable(io.axi_io.ar.addr,    io.axi_io.ar.valid))
    val awaddr              =   Mux(accept_write, io.axi_io.aw.addr,        RegEnable(io.axi_io.aw.addr,    io.axi_io.aw.valid))
    val wdata               =   Mux(io.axi_io.w.valid, io.axi_io.w.data,    RegEnable(io.axi_io.w.data,     io.axi_io.w.valid))
    val wstrb               =   Mux(io.axi_io.w.valid, io.axi_io.w.strb,    RegEnable(io.axi_io.w.strb,     io.axi_io.w.valid)) 

    io.out.dr.en      := !is_write && state === s_inflight
    io.out.dr.addr    := araddr
    io.out.dw.en      := is_write&&(state === s_inflight)
    io.out.dw.addr    := awaddr
    // when wvalid high, io.out.dw.data should valid
    io.out.dw.data    := wdata
    io.out.dw.mask    := wstrb


    val resp        =   0.U  // OKAY
    val resp_hold = Mux((state === s_inflight),resp, RegNext(resp))  

    io.axi_io.r.resp    := resp_hold
    io.axi_io.r.valid   := !is_write && (state === s_inflight && io.out.dr.ready) || (state === s_wait_rready_bready)
    io.axi_io.r.data    := Mux((state === s_inflight),io.out.dr.data, RegEnable(io.out.dr.data,(state === s_inflight)))  
    io.axi_io.r.last    := io.axi_io.r.valid 
    io.axi_io.b.valid   := is_write && (((state === s_inflight) && io.out.dw.ready ) || (state === s_wait_rready_bready))
    io.axi_io.b.resp    := resp_hold
}


