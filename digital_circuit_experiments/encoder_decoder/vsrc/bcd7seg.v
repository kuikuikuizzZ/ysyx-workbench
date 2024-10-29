module bcd7seg(
  input [2:0] code,
  input clk,
  output reg[7:0] seg
);

    always@(*)
        case(code)
            3'h0 : seg = ~8'b11111101;
            3'h1 : seg = ~8'b01100000;
            3'h2 : seg = ~8'b11011010;
            3'h3 : seg = ~8'b11110010;
            3'h4 : seg = ~8'b01100110;
            3'h5 : seg = ~8'b10110110;
            3'h6 : seg = ~8'b10111110;
            3'h7 : seg = ~8'b11100000;
            // 4'h8 : seg = 8'b11111110;
            // 4'h9 : seg = 8'b11110110;
            // 4'ha : seg = 8'b11101110;
            // 4'hb : seg = 8'b00111110;
            // 4'hc : seg = 8'b10011100;
            // 4'hd : seg = 8'b01111010;
            // 4'he : seg = 8'b10011110;
            // 4'hf : seg = 8'b10001110;
            // default seg = 8'b00000000;
        endcase

endmodule
