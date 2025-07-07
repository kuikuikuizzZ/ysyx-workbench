
module YSYX2400012AsyncMem #(
    ADDR_WIDTH = 32,
    DATA_WIDTH = 32,
    ORIGIN_ADDR=32'h80000000,
    MEM_SIZE=32'h08000000
) (
    // // // 全局时钟（根据Chisel的MemIo需补充）
    // input clk,
    input clock,
    input reset,
    // 写端口（dw: Wport）
    input                  dw_en,           // 写使能 (原MemWEn)
    input  [ADDR_WIDTH-1:0] dw_addr,        // 写地址
    input  [DATA_WIDTH-1:0] dw_data,        // 写数据
    input  [DATA_WIDTH-1:0] dw_len,         // 字节掩码 (原Length整合至mask)

    // 读端口（dr: Rport)）
    input  [ADDR_WIDTH-1:0] dr_addr,        // 端口0地址
    input  dr_en,                          // 端口使能
    output  reg [DATA_WIDTH-1:0] dr_data   // 端口数据
);

    // 1. 重构读写逻辑分离
    //-----------------------------
    // 写逻辑：使用dw_en触发pmem_write
    
    always @(*) begin
        if (dw_en) begin
            pmem_write(dw_addr, dw_len, dw_data); // mask替代Length
        end
        // $display("PMEM WRITE: addr=%h, len=%h, data=%h", dw_addr, dw_len, dw_data);
    end

    always @(*) begin
        if (reset) begin
            dr_data = 32'b0; // 重置端口1数据
        end else if (dr_en) begin
            // 端口1读取
            pmem_read(dr_addr, 4, dr_data);
        end else begin
            // 端口1读取
            dr_data = 32'b0; // 重置端口1数据
        end
    end

endmodule