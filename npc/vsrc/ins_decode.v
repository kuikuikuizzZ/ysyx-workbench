module ysyx_24100012_inst_decode #(DATA_WIDTH) (
    input [DATA_WIDTH-1:0] instruction,
    output reg [DATA_WIDTH-1:0]  imm ,
    output reg [3:0]        func7_6_func3,
    output reg [3:0]        ALUSel,
    output reg [2:0]        instType,
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
    parameter [1:0] WBALU=2'b00, WBPc=2'b01,WBLoad=2'b10,WBNone=2'b11;;
    
    wire [6:0] opcode; 
    wire [2:0] func3;
    wire [DATA_WIDTH-1:0] imm_S,imm_I,imm_Is,imm_B,imm_J,imm_U;
    
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

    ysyx_24100012_MuxKeyWithDefault #(3,3,4) mul_ALUSel (
        ALUSel,
        instType,
        4'h0,{
            R_Type,{instruction[30], func3},
            I_Type,{1'b0, func3},
            Is_Type,{instruction[30], func3}
        }); 


    always @ (*) begin
        case (opcode)
            // R type
            7'b0110011: begin
                func7_6_func3 = {instruction[30], func3};
                imm = 0;
                instType = R_Type;
                {rs2,rs1,rd} = {instruction[24:20],instruction[19:15],instruction[11:7]};  
                WEn=1'b1;
                ASel=0;
                BSel=0;
                WBSel=WBALU;
            end
            // I type
            7'b0010011: begin
                // I* type
                if (func3==3'b101 | func3==3'b001) begin
                    func7_6_func3 = { instruction[30], func3};
                    imm = imm_Is;
                    instType = Is_Type; 
                end else  begin 
                    func7_6_func3[2:0] = func3;
                    imm = imm_I;
                    instType = I_Type;
                end
                {rs2,rs1,rd} = {5'b0,instruction[19:15],instruction[11:7]};
                WEn=1'b1;
                ASel=0;
                BSel=1;
                WBSel=WBALU;
            end
            // B type
            7'b1100011: begin
                func7_6_func3[2:0] = func3;
                imm = imm_B;
                instType = B_Type;
                {rs2,rs1,rd} = {instruction[24:20],instruction[19:15],instruction[11:7]};  
                WEn=1'b0;
                ASel=1;
                BSel=1;
                WBSel=WBNone;
            end
            // L type
            7'b0000011: begin
                func7_6_func3[2:0] = func3;
                imm = imm_I;
                instType =  L_Type;
                {rs2,rs1,rd} = {5'b0,instruction[19:15],instruction[11:7]};
                WEn=1'b1;
                ASel=0;
                BSel=1;
                WBSel=WBLoad;
            end
            // S type
            7'b0100011: begin
                func7_6_func3[2:0] = func3;
                imm = imm_S;
                instType = S_Type;
                {rs2,rs1,rd} = {instruction[24:20],instruction[19:15],instruction[11:7]};  
                WEn=1'b0;
                ASel=0;
                BSel=1;
                WBSel=WBNone;
            end
            
            // jal 
            7'b1101111: begin
                func7_6_func3 = 4'h0;
                imm = imm_J;
                instType = J_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,instruction[11:7]};
                WEn=1'b1;
                ASel=1;
                BSel=1;   
                WBSel=WBPc;  
            end
            // jalr
            7'b1100111: begin
                // TODO: func3 000 not use
                func7_6_func3[2:0] = func3;
                imm = imm_I;
                instType = J_Type;
                {rs2,rs1,rd} = {5'b0,instruction[19:15],instruction[11:7]};
                WEn=1'b1;
                ASel=0;
                BSel=1;  
                WBSel=WBPc;           
            end
            // auipc
            7'b0010111: begin
                func7_6_func3 = 4'b0;
                imm = imm_U;
                instType = U_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,instruction[11:7]};
                WEn=1'b1;
                ASel=1;
                BSel=1;
                WBSel=WBALU;
            end
            // lui
            7'b0110111: begin
                func7_6_func3 = 4'b0;
                imm = imm_U;
                instType = U_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,instruction[11:7]};
                WEn=1'b1;
                ASel=0;
                BSel=1;
                WBSel=WBALU;
            end
            // ebreak, ecall
            7'b1110011: begin
                func7_6_func3[2:0] = func3;
                imm = imm_I;
                instType =  I_Type;
                {rs2,rs1,rd} = {5'b0,instruction[19:15],instruction[11:7]};
                WEn=1'b0;
                ASel=0;
                BSel=1;
                WBSel=WBNone;
            end
            default: begin
                func7_6_func3 = 4'b0;
                imm=0;
                instType = I_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,5'b0};
                WEn=1'b0;
                ASel=0;
                BSel=0;
                WBSel=WBALU;
            end
        endcase
    end
endmodule // ysyx_24100012_inst_decode
