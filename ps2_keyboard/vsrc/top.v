module top(
    input clk,
    input rst,
    input [7:0] sw,
    input ps2_clk,
    input ps2_data,
    output [15:0] ledr,
    output [7:0] seg0,
    output [7:0] seg1,
    output [7:0] seg2,
    output [7:0] seg3,
    output [7:0] seg4,
    output [7:0] seg5
);

wire ready,overflow,nextdata_n ;
reg [7:0] data;


ps2_keyboard my_keyboard(
    .clk(clk),
    .clrn(~rst),
    .ps2_clk(ps2_clk),
    .ps2_data(ps2_data),
    .data(data),
    .ready(ready),
    .nextdata_n(nextdata_n),
    .overflow(overflow)
);


assign ledr[2:0] =  {ready,nextdata_n,overflow};

bcd7seg my_seg(
    .clk(clk),
    .rst(sw[0]),
    .ready(ready),
    .data(data),
    .o_seg0(seg0),
    .o_seg1(seg1),
    .o_seg2(seg2),
    .o_seg3(seg3),
    .o_seg4(seg4),
    .o_seg5(seg5),
    .nextdata_n(nextdata_n)
);

endmodule

