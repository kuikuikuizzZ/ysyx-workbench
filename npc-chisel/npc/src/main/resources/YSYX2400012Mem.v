import "DPI-C" function void pmem_read(input int outaddr,input int length, output int dout);
import "DPI-C" function void pmem_write(input int inaddr,input int length, input int din);

module YSYX2400012Mem #(
    ADDR_WIDTH = 32,
    DATA_WIDTH = 32,
    ORIGIN_ADDR=32'h80000000,
    MEM_SIZE=32'h08000000
) (
    // // 全局时钟（根据Chisel的MemIo需补充）
    input clk,
    
    // 指令读端口（Vec(2, Rport)）
    input  [ADDR_WIDTH-1:0] dataInstr_0_addr,  // 端口0地址
    input  [ADDR_WIDTH-1:0] dataInstr_1_addr,  // 端口1地址


    // 数据写端口（dw: Wport）
    input                  dw_en,           // 写使能 (原MemWEn)
    input  [ADDR_WIDTH-1:0] dw_addr,         // 写地址
    input  [DATA_WIDTH-1:0] dw_data,         // 写数据
    input  [DATA_WIDTH-1:0] dw_len,        // 字节掩码 (原Length整合至mask)
    output reg [DATA_WIDTH-1:0] dataInstr_0_data,   // 端口0数据
    output reg [DATA_WIDTH-1:0] dataInstr_1_data   // 端口1数据
);

    // 1. 重构读写逻辑分离
    //-----------------------------
    // 写逻辑：使用dw_en触发pmem_write
    always @(posedge clk) begin
        if (dw_en) begin
            pmem_write(dw_addr, dw_len, dw_data); // mask替代Length
        end
    end

    // 读逻辑：双端口独立读取
    reg [DATA_WIDTH-1:0] read_buf [0:1]; // 双缓冲避免组合环路

    always @(*) begin
        // 端口0读取
        pmem_read(dataInstr_0_addr, 4, read_buf[0]); // 固定32位=4字节
        assign dataInstr_0_data = read_buf[0];
        
        // 端口1读取
        pmem_read(dataInstr_1_addr, 4, read_buf[1]); 
        assign dataInstr_1_data = read_buf[1];
    end

endmodule