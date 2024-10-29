module mux41(
  input  [9:0] sw,
  input clk,
  input rst,
  output [1:0] ledr);

  MuxKey #(4, 2, 2) muxkey41 (ledr, sw[1:0], {
    2'b00, sw[3:2],
    2'b01, sw[5:4],
    2'b10, sw[7:6],
    2'b11, sw[9:8]
  });
endmodule
