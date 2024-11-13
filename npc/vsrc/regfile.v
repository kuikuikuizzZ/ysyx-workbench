module ysyx_24100012_regfiles #(WIDTH,N_REG,INDEX_LEN)(
    input clk,
    input rst,
    // input [WIDTH-1:0] RegWriteData,
    input [INDEX_LEN:0] RegWriteIndex,
    input [INDEX_LEN:0] RegReadIndex1,
    input [INDEX_LEN:0] RegReadIndex2,
    // input RegWEn,
    reg output [WIDTH-1,0] RegReadData1,
    reg output [WIDTH-1,0] RegReadData2,
)

wire [WIDTH-1:0] reg_input_list[N_REG-1:0];
// wire reg_wen_list[N_REG-1:0];

genvar i;
generate
    for (i=0;i<N_REG;i=i+1) begin: x
    ysyx_24100012_Reg #(WIDTH,0) x (clk,rst,
        reg_input_list[i],
        reg_output_list[i],
        reg_wen_list[i]);
    end
endgenerate


MuxKey #(N_REG,INDEX_LEN,WIDTH) RegRead1 (
    RegReadData1,
    RegReadIndex1,
    {
        5'b00,reg_input_list[0],
        5'b01,reg_input_list[1],
        5'b02,reg_input_list[2],
        5'b03,reg_input_list[3],
        5'b04,reg_input_list[4],
        5'b05,reg_input_list[5],
        5'b06,reg_input_list[6],
        5'b07,reg_input_list[7],
        5'b08,reg_input_list[8],
        5'b09,reg_input_list[9],
        5'b10,reg_input_list[10],
        5'b11,reg_input_list[11],
        5'b12,reg_input_list[12],
        5'b13,reg_input_list[13],
        5'b14,reg_input_list[14],
        5'b15,reg_input_list[15],
        5'b16,reg_input_list[16],
        5'b17,reg_input_list[17],
        5'b18,reg_input_list[18],
        5'b19,reg_input_list[19],
        5'b20,reg_input_list[20],
        5'b21,reg_input_list[21],
        5'b22,reg_input_list[22],
        5'b23,reg_input_list[23],
        5'b24,reg_input_list[24],
        5'b25,reg_input_list[25],
        5'b26,reg_input_list[26],
        5'b27,reg_input_list[27],
        5'b28,reg_input_list[28],
        5'b29,reg_input_list[29],
        5'b30,reg_input_list[30],
        5'b31,reg_input_list[31]
    });
MuxKey #(N_REG,INDEX_LEN,WIDTH) RegRead2 (
    RegReadData2,
    RegReadIndex2,
    {
        5'b00,reg_input_list[0],
        5'b01,reg_input_list[1],
        5'b02,reg_input_list[2],
        5'b03,reg_input_list[3],
        5'b04,reg_input_list[4],
        5'b05,reg_input_list[5],
        5'b06,reg_input_list[6],
        5'b07,reg_input_list[7],
        5'b08,reg_input_list[8],
        5'b09,reg_input_list[9],
        5'b10,reg_input_list[10],
        5'b11,reg_input_list[11],
        5'b12,reg_input_list[12],
        5'b13,reg_input_list[13],
        5'b14,reg_input_list[14],
        5'b15,reg_input_list[15],
        5'b16,reg_input_list[16],
        5'b17,reg_input_list[17],
        5'b18,reg_input_list[18],
        5'b19,reg_input_list[19],
        5'b20,reg_input_list[20],
        5'b21,reg_input_list[21],
        5'b22,reg_input_list[22],
        5'b23,reg_input_list[23],
        5'b24,reg_input_list[24],
        5'b25,reg_input_list[25],
        5'b26,reg_input_list[26],
        5'b27,reg_input_list[27],
        5'b28,reg_input_list[28],
        5'b29,reg_input_list[29],
        5'b30,reg_input_list[30],
        5'b31,reg_input_list[31]
    });
endmodule