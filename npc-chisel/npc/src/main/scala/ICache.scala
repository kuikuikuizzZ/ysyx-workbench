
// package npc

// import chisel3._
// import chisel3.util._
// import npc.common._
// import npc.Constants._

// class ICacheIO(implicit val conf: ysyx_24100012_Config) extends Bundle {
//   val port      = new MemPortIo(conf.xlen)
//   val pc        = Input(UInt(conf.xprlen.W))
//   val req_valid = Input(Bool())
//   val inst      = Output(UInt(conf.xlen.W))
//   val valid     = Output(Bool())
// }

// class ICache(implicit val conf: ysyx_24100012_Config) extends Module { 
//     val io = IO(new ICacheIO)
//     // tag bits = 32-4-2 = 26 (16 = 2^4,4 = 2^2 bytes)
//     // valid bits = 1, tag bits = 26, 32 (4bytes) 
//     // 1+ 26 +32 = 59
//     val mem = SyncReadMem(conf.ICacheSize,UInt(conf.ICacheBlockBits.W))

//     val cache_data = mem.read(io.pc(5,2),io.req_valid)
//     val cache_valid = cache_data(58)
//     val hit = cache_valid && (io.pc(31,6) === cache_data(57,32))
//     when(cache_inst(58) && ){
//       io.inst := cache_data(31:0)
//       io.valid := true.B
//     } .(){

//     }
//     io.port.req.valid := !hit && !io.reset
//     io.port.req.bits.addr := pc_reg
//     io.port.req.bits.fcn := M_XRD
//     io.port.req.bits.typ := MT_WU
//     when 


//     io.inst := Mux(io.hit,cache_data(31:0),0.U)
//     io.valid := Mux(io.hit,true.B,false.B)

// }