module top(
    input clk,
    input rst,
    input [15:0] sw,
    output [15:0] ledr,
    output [7:0] seg0
);
    reg [3:0] A = sw[3:0];
    reg [3:0] B = sw[7:4];
    reg [7:0] Result;
    reg [1:0] carry,overflow,zero;
    reg [3:0] gate_out;
    reg gate_zero;
    reg [2:0] op = sw[15:13];

    assign ledr[11:9] = op;

    adder4 add (A,B,0,Result[3:0],carry[0],overflow[0],zero[0]);

    adder4 sub (A,B,1,Result[7:4],carry[1],overflow[1],zero[1]);

    gates g (A,B,op,gate_out,gate_zero);

    always@(*) begin
    case(op)
        3'b000: begin
            ledr[3:0] = Result[3:0];
            ledr[7:5] = {carry[0],overflow[0],zero[0]};
        end
        3'b001: begin
            ledr[3:0] = Result[7:4];
            ledr[7:5] = {carry[1],overflow[1],zero[1]};
        end
        3'b010,3'b011,3'b100,3'b101: begin
            ledr[3:0] = gate_out;
            ledr[5] = gate_zero;
        end
        3'b110:  
            ledr[0] = Result[7]^overflow[1];
        3'b111: 
            ledr[0] = zero[1];
    endcase
    end
endmodule
