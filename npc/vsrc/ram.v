import "DPI-C" function void pmem_read(input int outaddr,input int length, output int dout);
import "DPI-C" function void pmem_write(input int inaddr,input int length, input int din);

module ysyx_24100012_ram #(ADDR_WIDTH,DATA_WIDTH,ORIGIN_ADDR,MEM_SIZE)(
    input clk,
    input MemWEn,
    input [DATA_WIDTH-1:0] length,
    // input sign,
    input [DATA_WIDTH-1:0] din,
    input [ADDR_WIDTH-1:0] inaddr,
    input [ADDR_WIDTH-1:0] outaddr,
    output reg [DATA_WIDTH-1:0] dout
);
    always @(*) begin
        if (MemWEn) begin
            pmem_write(inaddr,length,din);
        end
        pmem_read(outaddr,length,dout);
    end
    
endmodule
