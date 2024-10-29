module top(
    input clk,
    input rst,
    input [8:0] sw,
    output [4:0] ledr,
    output [7:0] seg0
);

bcd7seg seg1(
    ledr[2:0],
    clk,
    seg0
);

encoder83 pe (
    sw[7:0],
    sw[8],
    clk,
    ledr[2:0],
    ledr[4]
);

endmodule
