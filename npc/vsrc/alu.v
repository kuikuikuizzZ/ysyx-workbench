module ysyx_24100012_alu #(WIDTH,N_SEL)(
    input [WIDTH-1:0] in_a,
    input [WIDTH-1:0] in_b,
    input [2:0] inst_type,
    input [N_SEL-1:0] alu_sel,
    output [WIDTH-1:0] out,
)
    always@(*) begin
        case(alu_sel)
            4'b0000: out=in_a+in_b;
            default: out = 0;
        endcase
    end
endmodule

