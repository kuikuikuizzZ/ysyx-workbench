module ysyx_24100012_alu #(DATA_WIDTH=32,N_SEL=10)(
    input clk,
    input rst,
    input [DATA_WIDTH-1:0] in_a,
    input [DATA_WIDTH-1:0] in_b,
    input [2:0] inst_type,
    input [N_SEL-1:0] alu_sel,
    output reg [DATA_WIDTH-1:0] out
);
    wire [DATA_WIDTH-1:0] mid_out;
    ysyx_24100012_MuxKey #(10,4,DATA_WIDTH) alu (
        out,
        alu_sel,{
            4'b0000, in_a+in_b,                         //add
            4'b1000, in_a-in_b,                         //sub
            4'b0001, in_a<<in_b,                        //slt
            4'b0010, ($signed(in_a)<$signed(in_b))? 32'h1 :32'h0,   //slt
            4'b0011, (in_a<in_b)? 32'h1 :32'h0,         //sltu
            4'b0100, in_a^in_b,                         //xor
            4'b0101, in_a>>in_b,                        //srl
            4'b1101, $signed(in_a)>>in_b,               //sra
            4'b0110, in_a | in_b,                       //or
            4'b0111, in_a & in_b                        //and
        });
    // always@(*)
    //     $display("alu a: %x, b: %x, code %b", in_a,in_b,alu_sel);

endmodule

