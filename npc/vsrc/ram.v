import "DPI-C" function void pmem_read(input int  outaddr, output int  dout);

module ysyx_24100012_ram #(ADDR_WIDTH,DATA_WIDTH,ORIGIN_ADDR,MEM_SIZE)(
    input clk,
    input we,
    input [DATA_WIDTH-1:0] din,
    input [ADDR_WIDTH-1:0] inaddr,
    input [ADDR_WIDTH-1:0] outaddr,
    output reg [DATA_WIDTH-1:0] dout
);
    always @(posedge clk)
        // if (we)
        //     ram[in_index] <= din;
    pmem_read(outaddr,dout);
endmodule
