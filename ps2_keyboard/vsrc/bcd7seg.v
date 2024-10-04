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
  output reg[7:0] o_seg5,
  output nextdata_n
);
  reg [7:0] count,ascii_num;
  reg [7:0] num1,num2,num3,num4,num5,num6,new1,new2,new4,new3;
  rom r1(data,ascii_num);
  bcd b0(data[3:0],new1);
  bcd b1(data[7:4],new2);
  bcd b2(ascii_num[3:0],new3);
  bcd b3(ascii_num[7:4],new4);
  bcd b4(count[3:0],num5);
  bcd b5(count[7:4],num6);
  /* current_state
  000: idle
  001: ready+input
  010: ready+f0
  */ 
  reg [2:0] current_state,next_state;

  always @(posedge clk) begin
    if (rst)  begin 
      current_state=3'b000;
      count =0;
    end
    else current_state = next_state;
  end

  always@(current_state or data) begin
    case(current_state)
      3'b000: begin
          num1 = ~8'b00000000;
          num2 = ~8'b00000000;
          num3 = ~8'b00000000;
          num4 = ~8'b00000000;
          if (!ready) next_state = current_state;  
          else begin
              nextdata_n = 0;
              if (data!=8'hf0) begin
                  num1 = new1;
                  num2 = new2;
                  num3 = new3;
                  num4 = new4;
                  next_state = 3'b001;
              end else if (data==8'hf0) begin
                  next_state = 3'b010;
              end else begin
                  next_state = current_state;
              end
          end
      end
      3'b001: begin
          if (!ready) next_state = current_state;  
          else begin
              nextdata_n = 0;
              if (data!=8'hf0) begin
                  num1 = new1;
                  num2 = new2;
                  num3 = new3;
                  num4 = new4;
                  next_state = current_state;
              end else if (data==8'hf0) begin
                  next_state = 3'b010;
              end else begin
                  next_state = current_state;
              end
          end
      end
      3'b010: begin
          if (!ready) next_state = current_state;  
          else begin
              nextdata_n = 0;
              if (data!=8'hf0) begin
                  next_state = 3'b000;
                  count = count+1;
              end else 
                  next_state = current_state;
          end
          num1 = ~8'b00000000;
          num2 = ~8'b00000000;
          num3 = ~8'b00000000;
          num4 = ~8'b00000000;
      end
      // 3'b011: begin
      //     nextdata_n = 0;
      //     next_state = 3'b000;
      // end
      default:
          next_state = current_state;
    endcase
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