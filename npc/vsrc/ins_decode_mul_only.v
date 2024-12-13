module ysyx_24100012_inst_decode_mul_only #(DATA_WIDTH=32) (
    input clk,
    input [DATA_WIDTH-1:0] instruction,
    output reg [DATA_WIDTH-1:0]  imm ,
    output reg [2:0]        func3,
    output reg [3:0]        ALUSel,
    output reg [1:0]        PCType,
    output reg [4:0]        rs1,
    output reg [4:0]        rs2,
    output reg [4:0]        rd,
    output reg              ASel,
    output reg              BSel,
    output reg              WEn,
    output reg              MemWEn,
    output reg              MemREn,
    output reg [1:0]        WBSel    
);
    parameter [2:0] R_Type=3'b000,I_Type=3'b001,B_Type=3'b010, S_Type=3'b100;
    parameter [2:0] J_Type=3'b011,U_Type=3'b101, Is_Type = 3'b110, L_Type=3'b111;
    parameter [1:0] WBALU=2'b00, WBPc=2'b01,WBLoad=2'b10,WBNone=2'b11;
    
    wire [6:0] opcode; 
    wire [3:0] ALUSel_I;
    wire [DATA_WIDTH-1:0] imm_S,imm_I,imm_Is,imm_B,imm_J,imm_U,imm_I_temp;
    assign opcode = instruction[6:0];
    assign func3  = instruction[14:12];
    assign imm_S = { {21{instruction[31]}},instruction[30:25],instruction[11:7]};
    assign imm_I = { {21{instruction[31]}}, instruction[30:20]};
    assign imm_Is = {27'b0,instruction[24:20]};
    assign imm_B = { {20{instruction[31]}},instruction[7],instruction[30:25],instruction[11:8],1'b0};
    assign imm_J = { {12{instruction[31]}},instruction[19:12],instruction[20],instruction[30:21],1'b0};
    assign imm_U =  { instruction[31:12],12'b0};

    ysyx_24100012_MuxKey #(1,7) mul_MemREn  (
        MemREn,
        opcode,{
            7'b0000011,1'b1
        }); 
    ysyx_24100012_MuxKey #(1,7) mul_MemWEn (
        MemWEn,
        opcode,{
            7'b0100011,1'b1
        }); 
    ysyx_24100012_MuxKeyWithDefault #(3,7,2) mul_PCType (
        PCType,
        opcode,
        2'b0,{
            7'b1101111,2'b01,
            7'b1100111,2'b01,
            7'b1100011,2'b10
        }); 
    ysyx_24100012_MuxKeyWithDefault #(2,3,4) mul_ALUSel_I (
        ALUSel_I,
        func3,
        {1'b0, func3},{
            3'b101,{instruction[30], func3},
            3'b001,{instruction[30], func3}
        }); 
    ysyx_24100012_MuxKeyWithDefault #(2,7,4) mul_ALUSel (
        ALUSel,
        opcode,
        4'h0,{
            7'b0110011,     {instruction[30], func3},
            7'b0010011,     ALUSel_I
        }); 
    ysyx_24100012_MuxKeyWithDefault #(2,3,32) mul_immI (
        imm_I_temp,
        func3,
        imm_I,{
            3'b101,imm_Is,
            3'b001,imm_Is
        }); 



    ysyx_24100012_MuxKeyWithDefault #(10,7,5) mul_opcode (
        {WEn,ASel,BSel,WBSel} ,
        opcode,
        5'h0,{
            7'b0110011, 5'b10000,                 // R Type
            7'b0010011, 5'b10100,                 // I type
            7'b1100011, 5'b01100,                 // B type
            7'b0000011, 5'b10110,                 // L type
            7'b0100011, 5'b00111,                 // S type
            7'b1101111, 5'b11101,                 // jal 
            7'b1100111, 5'b10001,                 // jalr
            7'b0010111, 5'b11100,                 // auipc
            7'b0110111, 5'b10100,                 // lui
            7'b1110011, 5'b00111                  // ebreak, ecall
        });
    ysyx_24100012_MuxKeyWithDefault #(10,7,15) mul_rs (
        {rs2,rs1,rd},
        opcode,
        15'h0,{
            7'b0110011, {instruction[24:20],instruction[19:15],instruction[11:7]},  // R Type
            7'b0010011, {5'b0,instruction[19:15],instruction[11:7]},                // I type
            7'b1100011, {instruction[24:20],instruction[19:15],instruction[11:7]},  // B type
            7'b0100011, {instruction[24:20],instruction[19:15],instruction[11:7]},  // S type
            7'b0000011, {5'b0,instruction[19:15],instruction[11:7]},                // L type
            7'b1101111, {5'b0,5'b0,instruction[11:7]},                              // jal 
            7'b1100111, {5'b0,instruction[19:15],instruction[11:7]},                // jalr
            7'b0010111, {5'b0,5'b0,instruction[11:7]},                              // auipc
            7'b0110111, {5'b0,5'b0,instruction[11:7]},                              // lui
            7'b1110011, {5'b0,instruction[19:15],instruction[11:7]}                 // ebreak, ecall
        });  
    ysyx_24100012_MuxKeyWithDefault #(10,7,32) mul_imm (
        imm,
        opcode,
        32'h0,{
            7'b0110011, 32'b0,                 // R Type
            7'b0010011, imm_I_temp,            // I type
            7'b1100011, imm_B,                 // B type
            7'b0000011, imm_I,                 // L type
            7'b0100011, imm_S,                 // S type
            7'b1101111, imm_J,                 // jal 
            7'b1100111, imm_I,                 // jalr
            7'b0010111, imm_U,                 // auipc
            7'b0110111, imm_U,                 // lui
            7'b1110011, imm_I                  // ebreak, ecall
        }); 
endmodule // ysyx_24100012_inst_decode_mul_only
