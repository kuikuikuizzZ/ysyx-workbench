module top(
    input clk,
    input rst,
    input [15:0] sw,
    input [1:0] btn,
    output [7:0] seg0,
    output [7:0] seg1
);
    reg [3:0] num1,num2;
    shifter8 s8 (sw[7:0],btn[0],btn[1],{num2,num1});
    bcd7seg h1(num1,seg0);
    bcd7seg h2(num2,seg1);
endmodule
