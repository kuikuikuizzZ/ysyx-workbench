module ysyx_24100012_ram #(ADDR_WIDTH,DATA_WIDTH,ORIGIN_ADDR,MEM_SIZE)(
    input clk,
    input we,
    input [DATA_WIDTH-1:0] din,
    input [ADDR_WIDTH-1:0] inaddr,
    input [ADDR_WIDTH-1:0] outaddr,
    output [DATA_WIDTH-1:0] dout
);


    wire [DATA_WIDTH-1:0] in_index,out_index;
    reg [DATA_WIDTH-1:0] ram [MEM_SIZE-1:0];

    assign in_index = inaddr-ORIGIN_ADDR;
    assign out_index = outaddr-ORIGIN_ADDR;

    always @(posedge clk)
        if (we)
            ram[in_index] <= din;

    assign dout = ram[out_index];
endmodule
