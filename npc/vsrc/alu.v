module ysyx_24100012_alu #(DATA_WIDTH,N_SEL)(
    input clk,
    input rst,
    input [DATA_WIDTH-1:0] in_a,
    input [DATA_WIDTH-1:0] in_b,
    input [2:0] inst_type,
    input [N_SEL-1:0] alu_sel,
    output reg [DATA_WIDTH-1:0] out
);
    always@(*) begin
        case(alu_sel)
            
            4'b0000: begin
                out = in_a+in_b;
            end
            default: out = 0;
        endcase
    end
endmodule

