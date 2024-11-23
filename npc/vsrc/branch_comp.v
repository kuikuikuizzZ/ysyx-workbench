
module ysyx_24100012_branch_comp #(ADDR_WIDTH,DATA_WIDTH) (
    input [3:0] func7_6_func3,
    input [2:0] instType,
    input [DATA_WIDTH-1:0] rs1,
    input [DATA_WIDTH-1:0] rs2,    
    output PCSel
);
    wire [2:0] func3;
    wire [DATA_WIDTH-1:0] signedRes, unSignRes;
    wire branchPCSel;
    assign signedRes = $signed(rs1)-$signed(rs2);
    assign unSignRes = rs1-rs2;
    assign func3 = func7_6_func3[2:0];
    ysyx_24100012_MuxKeyWithDefault #( 6,3 ) branch_comp (
        branchPCSel,
        func3,
        1'b0,{
        3'b000, unSignRes == 0,
        3'b001, unSignRes != 0,
        3'b100, $signed(signedRes)<0,
        3'b101, $signed(signedRes)>=0,
        3'b110, unSignRes>rs1,
        3'b111, unSignRes<=rs1
    });
    ysyx_24100012_MuxKeyWithDefault #(
        2,3) mul_pc (
        PCSel,
        instType,
        1'b0,{
        3'b010, branchPCSel,        // B_Type
        3'b011, 1'b1                // J_Type
    });

endmodule