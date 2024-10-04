module bcd7seg(
  input clk,
  input rst,
  input ready,
  input [7:0] data,
  output reg[7:0] o_seg0,
  output reg[7:0] o_seg1,
  output reg[7:0] o_seg2,
  output reg[7:0] o_seg3,
  output reg[7:0] o_seg4,
  output reg[7:0] o_seg5
);

  reg [7:0] num1,num2,num3,num4,num5,num6,new1,new2;
  bcd b1(data[3:0],new1);
  bcd b2(data[3:0],new2);


  always@(posedge clk) begin
    num5 = num3;
    num6 = num4;
    num3 = num1;
    num4 = num2;
    if (ready) begin
      num1 = new1;
      num2 = new2;
    end else begin
      num1 = 8'b00000001;
      num2 = 8'b00000001;
    end

  end
  assign o_seg0 = num1;
  assign o_seg1 = num2;
  assign o_seg2 = num3;
  assign o_seg3 = num4;
  assign o_seg4 = num5;
  assign o_seg5 = num6;
endmodule

module bcd (
    input [3:0] data,
    output reg [7:0] seg
);
    always@(*)
        case(data)
            4'h0 : seg = ~8'b11111101;
            4'h1 : seg = ~8'b01100000;
            4'h2 : seg = ~8'b11011010;
            4'h3 : seg = ~8'b11110010;
            4'h4 : seg = ~8'b01100110;
            4'h5 : seg = ~8'b10110110;
            4'h6 : seg = ~8'b10111110;
            4'h7 : seg = ~8'b11100000;
            4'h8 : seg = ~8'b11111110;
            4'h9 : seg = ~8'b11110110;
            4'ha : seg = ~8'b11101110;
            4'hb : seg = ~8'b00111110;
            4'hc : seg = ~8'b10011100;
            4'hd : seg = ~8'b01111010;
            4'he : seg = ~8'b10011110;
            4'hf : seg = ~8'b10001110;
            default seg = 8'b00000001;
        endcase
endmodule 