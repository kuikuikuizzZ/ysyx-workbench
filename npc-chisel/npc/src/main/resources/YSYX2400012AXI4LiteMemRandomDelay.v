
module YSYX2400012AXI4LiteMemRandomDelay #(
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
    reg [3:0] random_read, random_write;
    reg [3:0] num_read, num_write ;
    wire done_r,done_w ;
    LFSR lfsr_read (
        .clk(clock),
        .rst_n(reset),
        .enable(done_r),
        .random(random_read),
        .seed(4'b0101)
    );
    LFSR lfsr_write (
        .clk(clock),
        .rst_n(reset),
        .enable(done_w),
        .random(random_write),
        .seed(4'b0111)
    );
    assign done_r = num_read == random_read;
    assign done_w = num_write == random_write;
    always @(posedge clock) begin
        if (dw_en) begin
            if (done_w) begin
                pmem_mask_write(dw_addr, dw_mask_wide, dw_data); // mask替代Length
                dw_ready = 1'b1;
                num_write = 0;
            end else begin
                dw_ready = 1'b0;
                num_write = num_write + 1;
            end
            // $display("write dw_addr %x random_write %d, num: %d, dw_ready %d,dw_en %d done %d",dw_addr,random_write, num_write,dw_ready,dw_en,done_w);
        end 

    end

    always @(posedge clock) begin
        if (dr_en) begin
            if (done_r) begin 
                // -1 -> 1111
                pmem_mask_read(dr_addr, -1, dr_data);
                dr_ready = 1'b1;
                num_read = 0;
            end else begin
                dr_ready = 1'b0;
                num_read = num_read + 1;
                dr_data = 32'b0;
            end 
            // $display("read addr %x random %d, num: %d, dr_ready %d,dr_en %d done %d",dr_addr,random_read, num_read,dr_ready,dr_en,done_r);
        end
        
    end

endmodule