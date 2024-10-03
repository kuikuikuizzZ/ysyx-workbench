module top(
    // input clk,
    input [15:0]  sw,
    output [7:0]ledr,
    output [7:0] seg0,
    output [7:0] seg1
);
    reg [3:0] num1,num2;
    shifter8 s8 (sw[7:0],sw[14],sw[15],{num2,num1});
    bcd7seg h1(num1,seg0);
    bcd7seg h2(num2,seg1);
    assign ledr = {num2,num1};

endmodule
