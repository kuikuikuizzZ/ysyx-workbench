module ysyx_24100012_regfiles #(WIDTH,N_REG,INDEX_LEN)(
    input clk,
    input rst,
    // input [WIDTH-1:0] RegWriteData,
    input [INDEX_LEN:0] RegWriteIndex,
    input [INDEX_LEN:0] RegReadIndex1,
    input [INDEX_LEN:0] RegReadIndex2,
    input RegWEn,
    reg output [WIDTH-1,0] RegReadData1,
    reg output [WIDTH-1,0] RegReadData2,
)

wire [WIDTH-1:0] reg_input_list[N_REG-1:0];
// wire reg_wen_list[N_REG-1:0];

genvar i;
// zero register should equal to zero anytime.
ysyx_24100012_Reg #(WIDTH,0) x0 (clk,1'b1,reg_input_list[0],reg_output_list[0],reg_wen_list[0]);
generate
    for (i=1;i<N_REG;i=i+1) begin: x
    ysyx_24100012_Reg #(WIDTH,0) x (clk,rst,
        reg_input_list[i],
        reg_output_list[i],
        RegWEn);
    end
endgenerate

    always @(posedge clk) begin
        if (wen) rf[waddr] <= wdata;
    end
endmodule