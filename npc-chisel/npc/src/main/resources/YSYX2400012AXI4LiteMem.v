import "DPI-C" function void pmem_mask_read(input int outaddr,input int mask, output int dout);
import "DPI-C" function void pmem_mask_write(input int inaddr,input int mask, input int din);


module YSYX2400012AXI4LiteMem #(
    ADDR_WIDTH = 32,
    DATA_WIDTH = 32,
    MASK_WIDTH = 4,
    ORIGIN_ADDR=32'h80000000,
    MEM_SIZE=32'h08000000
) (
    // // // 全局时钟（根据Chisel的MemIo需补充）
    // input clk,
    input clock,
    input reset,

    // 写端口（dw: Wport）
    input                   dw_en,           // 写使能 (原MemWEn)
    input  [ADDR_WIDTH-1:0] dw_addr,        // 写地址
    input  [DATA_WIDTH-1:0] dw_data,        // 写数据
    input  [MASK_WIDTH-1:0] dw_mask,         // 字节掩码 (原Length整合至mask)
    // 读端口（dr: Rport)）
    input                   dr_en,          // 端口使能
    input  [ADDR_WIDTH-1:0] dr_addr,        // 端口0地址

    output  reg [DATA_WIDTH-1:0]    dr_data,   // 端口数据
    output                          dr_ready,
    output                          dw_ready
);
    wire [DATA_WIDTH-1:0] dw_mask_wide;
    mask_expander me (
        .mask_narrow(dw_mask),
        .mask_wide(dw_mask_wide)
    );
    always @(posedge clock) begin
        if (dw_en) begin
            pmem_mask_write(dw_addr, dw_mask_wide, dw_data); // mask替代Length
        end       
    end

    always @(posedge clock) begin
        if (dr_en) begin
            // -1 -> 1111
            pmem_mask_read(dr_addr, -1, dr_data);
        end else begin
            dr_data = 32'b0;
        end
 
        
    end

    assign dr_ready = 1'b1;
    assign dw_ready = 1'b1;

endmodule




module mask_expander #(parameter DATA_WIDTH = 32) (
    input [DATA_WIDTH/8-1:0] mask_narrow,
    output [DATA_WIDTH-1:0] mask_wide
);

function [DATA_WIDTH-1:0] expand_mask;
    input [DATA_WIDTH/8-1:0] mask_in;
    integer i;
    begin
        for (i = 0; i < DATA_WIDTH/8; i = i+1) begin
            expand_mask[i*8 +: 8] = {8{mask_in[i]}}; // 每个掩码位扩展为8比特
        end
    end
endfunction

assign mask_wide = expand_mask(mask_narrow); // 调用函数

endmodule