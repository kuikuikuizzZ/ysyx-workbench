module ysyx_24100012_regfiles #(
    ADDR_WIDTH,
    DATA_WIDTH,
    N_REG,
    INDEX_LEN)(
    input clk,
    input rst,
    input [DATA_WIDTH-1:0] RegWriteData,
    input [INDEX_LEN-1:0] RegWriteIndex,
    input [INDEX_LEN-1:0] RegReadIndex1,
    input [INDEX_LEN-1:0] RegReadIndex2,
    input RegWEn,
    output [DATA_WIDTH-1:0] RegReadData1,
    output [DATA_WIDTH-1:0] RegReadData2
);

wire [DATA_WIDTH-1:0] reg_input_list[N_REG-1:0];
wire [DATA_WIDTH-1:0] reg_output_list[N_REG-1:0];

// zero register should equal to zero anytime.
ysyx_24100012_Reg #(DATA_WIDTH,0) x0 (
    clk,1'b1,
    reg_input_list[0],
    reg_output_list[0],
    1'b0);

genvar i;
generate
    for (i=1;i<N_REG;i=i+1) begin: x
    ysyx_24100012_Reg #(DATA_WIDTH,0) x (clk,rst,
        reg_input_list[i],
        reg_output_list[i],
        RegWEn);
    end
endgenerate

assign reg_input_list[RegWriteIndex] = RegWriteData;
assign RegReadData1 = reg_output_list[RegReadIndex1];
assign RegReadData2 = reg_output_list[RegReadIndex2];

endmodule
