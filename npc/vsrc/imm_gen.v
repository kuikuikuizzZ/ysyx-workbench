module  ysyx_24100012_imm_gen #(WIDTH,N_IMM_SEL)(
    input [WIDTH-1:0] inst,
    input [2:0] imm_sel,
    output [WIDTH-1:0] data,
)

case (imm_sel)
    3'b000
endcase 


endmodule