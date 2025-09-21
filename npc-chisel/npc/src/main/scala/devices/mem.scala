package npc.devices

import chisel3._
import chisel3.util._

import npc.common.Constants._
import npc.common._

class AXI4LiteMem(implicit val conf: Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/AXI4LiteMem.v"
   addPath(path)
   println(s"AsyncMem path: ${path}")
}

class AXI4LiteMemRandomDelay(implicit val conf: Config) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle() { 
      val clock   =   Input(Clock())
      val reset   =   Input(Bool())
      val dr      =   new AXIRport(conf.xprlen, conf.xlen)
      val dw      =   new AXIWport(conf.xprlen, conf.xlen)
   })
   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/AXI4LiteMemRandomDelay.v"
   addPath(path)
   println(s"AXI4LiteMemRandomDelay path: ${path}")
}

class AsyncMem(val addrWidth: Int) extends BlackBox with HasBlackBoxPath {
   val io = IO(new Bundle{
      val dr = new AXIRport(addrWidth,32)
      val dw = new AXIWport(addrWidth,32)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 

   val path = System.getenv("NPC_CHISEL_HOME")+"/npc/src/main/resources/AsyncMem.v"
   addPath(path)
   println(s"AsyncMem path: ${path}")
}


class ysyx_24100012_AXI4LiteMem(implicit val conf: Config) extends BlackBox with HasBlackBoxInline  {
   val io = IO(new Bundle{
      val dr = new AXIRport(conf.xprlen, conf.xlen)
      val dw = new AXIWport(conf.xprlen, conf.xlen)
      val clock = Input(Clock())
      val reset = Input(Bool())
   }) 
   if (conf.ENABLE_IVERILOG){
            setInline("ysyx_24100012_AXI4LiteMem.v",
   """module ysyx_24100012_AXI4LiteMem #(
   |    ADDR_WIDTH = 32,
   |    DATA_WIDTH = 32,
   |    MASK_WIDTH = 4,
   |    ORIGIN_ADDR=32'h80000000,
   |    MEM_SIZE=32'h08000000
   |) (
   |    input clock,
   |    input reset,
   |
   |    // 写端口（dw: Wport）
   |    input                   dw_en,           // 写使能 (原MemWEn)
   |    input  [ADDR_WIDTH-1:0] dw_addr,        // 写地址
   |    input  [DATA_WIDTH-1:0] dw_data,        // 写数据
   |    input  [MASK_WIDTH-1:0] dw_mask,         // 字节掩码 (原Length整合至mask)
   |    // 读端口（dr: Rport)）
   |    input                   dr_en,          // 端口使能
   |    input  [ADDR_WIDTH-1:0] dr_addr,        // 端口0地址
   |
   |    output  reg [DATA_WIDTH-1:0]    dr_data,   // 端口数据
   |    output  reg                     dr_ready,
   |    output  reg                     dw_ready
   |);
   |  wire [DATA_WIDTH-1:0] dw_mask_wide;
   |  ysyx_24100012_mask_expander me (
   |      .mask(dw_mask),
   |      .mask_wide(dw_mask_wide)
   |  );
   |  reg [7:0] mem [80000:0];
   |  reg [2047:0] path = 0;
   |  initial begin
   |    if (!$value$plusargs("image=%s", path)) begin
   |      path = "./iverilog_scripts/dummy-riscv32e-npc.hex";
   |    end
   |    $display("Reading image from %s", path);
   |    $readmemh( path, mem,0,40000);
   |  
   |  end
   |    reg wen_reg;
   |    reg [31:0] wdata_reg,wmask_wide_reg,rdata_reg;
   |    wire [31:0] dr_addr_aligned;
   |    reg [31:0] dw_addr_aligned; 
   |    wire [31:0] wdata_masked = (wdata_reg & wmask_wide_reg) | (rdata_reg & ~wmask_wide_reg);
   |    assign dr_addr_aligned = {dr_addr[ADDR_WIDTH-1:2],2'b0}-32'h80000000 ;
   |    always @(posedge clock) begin
   |        if (dw_en) begin
   |           rdata_reg = {mem[dw_addr_aligned+3],mem[dw_addr_aligned+2],mem[dw_addr_aligned+1],mem[dw_addr_aligned]};
   |           wdata_reg <= dw_data;
   |           dw_addr_aligned = {dw_addr[ADDR_WIDTH-1:2],2'b0}-32'h80000000 ;
   |           wmask_wide_reg <= dw_mask_wide; 
   |           wen_reg <= 1'b1;
   |           dw_ready = 1'b1;
   |        end else if (wen_reg) begin
   |            mem[dw_addr_aligned+3] <= wdata_masked[31:24];
   |            mem[dw_addr_aligned+2] <= wdata_masked[23:16];
   |            mem[dw_addr_aligned+1] <= wdata_masked[15:8];
   |            mem[dw_addr_aligned] <= wdata_masked[7:0];
   |            wen_reg <= 1'b0;
   |            dw_ready = 1'b0;
   |            $display("write %x to %x mask %x,read %x",(wdata_reg & wmask_wide_reg) | (rdata_reg & ~wmask_wide_reg),dw_addr_aligned,wmask_wide_reg,rdata_reg);
   |        end else begin
   |            dw_ready = 1'b0;
   |        end
   |    end
   |     
   |    always @(posedge clock) begin
   |        if (dr_en) begin
   |            dr_data = {mem[dr_addr_aligned+3],mem[dr_addr_aligned+2],mem[dr_addr_aligned+1],mem[dr_addr_aligned]};
   |            dr_ready = 1'b1;
   |            $display("read %x from %x",dr_data,dr_addr_aligned);
   |        end else begin
   |            dr_data = 32'b0;
   |            dr_ready = 1'b0;
   |        end    
   |    end
   |
   |
   |endmodule
    """.stripMargin)
   } else {
      
   setInline("ysyx_24100012_AXI4LiteMem.v",
   """module ysyx_24100012_AXI4LiteMem #(
   |    ADDR_WIDTH = 32,
   |    DATA_WIDTH = 32,
   |    MASK_WIDTH = 4,
   |    ORIGIN_ADDR=32'h80000000,
   |    MEM_SIZE=32'h08000000
   |) (
   |    input clock,
   |    input reset,
   |
   |    // 写端口（dw: Wport）
   |    input                   dw_en,           // 写使能 (原MemWEn)
   |    input  [ADDR_WIDTH-1:0] dw_addr,        // 写地址
   |    input  [DATA_WIDTH-1:0] dw_data,        // 写数据
   |    input  [MASK_WIDTH-1:0] dw_mask,         // 字节掩码 (原Length整合至mask)
   |    // 读端口（dr: Rport)）
   |    input                   dr_en,          // 端口使能
   |    input  [ADDR_WIDTH-1:0] dr_addr,        // 端口0地址
   |
   |    output  reg [DATA_WIDTH-1:0]    dr_data,   // 端口数据
   |    output  reg                     dr_ready,
   |    output  reg                     dw_ready
   |);
   |
   |import "DPI-C" function void pmem_mask_write(input int inaddr,input int mask, input int din);
   |import "DPI-C" function void pmem_mask_read(input int outaddr,input int mask, output int dout);
   |
   |    wire [DATA_WIDTH-1:0] dw_mask_wide;
   |    ysyx_24100012_mask_expander me (
   |        .mask(dw_mask),
   |        .mask_wide(dw_mask_wide)
   |    );
   |    wire [31:0] dw_addr_aligned,dr_addr_aligned; 
   |    assign dw_addr_aligned = {dw_addr[ADDR_WIDTH-1:2],2'b0} ;
   |    assign dr_addr_aligned = {dr_addr[ADDR_WIDTH-1:2],2'b0} ;
   |    always @(posedge clock) begin
   |        if (dw_en) begin
   |            pmem_mask_write(dw_addr_aligned, dw_mask_wide, dw_data);
   |            dw_ready = 1'b1;
   |        end else begin
   |            dw_ready = 1'b0;
   |        end
   |    end
   |     
   |    always @(posedge clock) begin
   |        if (dr_en) begin
   |            // -1 -> 1111
   |            pmem_mask_read(dr_addr_aligned, -1, dr_data);
   |            dr_ready = 1'b1;
   |        end else begin
   |            dr_data = 32'b0;
   |            dr_ready = 1'b0;
   |        end
   |
   |    
   |    end
   |
   |    assign dw_ready = 1'b1;
   |
   |endmodule
    """.stripMargin)
   }


 }

