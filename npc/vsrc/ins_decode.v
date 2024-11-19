module ysyx_24100012_inst_decode #(DATA_WIDTH) (
    input [DATA_WIDTH-1:0] instruction,
    output reg [DATA_WIDTH-1:0]  imm ,
    output reg [3:0]        aluSel,
    output reg [2:0]        instType,
    output reg [4:0]        rs1,
    output reg [4:0]        rs2,
    output reg [4:0]        rd,
    output reg              ASel,
    output reg              BSel,
    output reg              PCSel,
    output reg              WEn,
    output reg [1:0]        WBSel    
);
    parameter [2:0] R_Type=3'b000,I_Type=3'b001,B_Type=3'b010, S_Type=3'b100;
    parameter [2:0] J_Type=3'b011,U_Type=3'b101, E_Type = 3'b110, No_Type=3'b111;
    parameter [1:0] WBALU=2'b00, WBLoad=2'b01,WBPc=2'b10,WBNone=2'b11;;
    wire [6:0] opcode; 
    wire [2:0] func3;
    assign opcode = instruction[6:0];
    assign func3  = instruction[14:12];
    always @ (*) begin
        case (opcode)
            // R type
            7'b0110011: begin
                aluSel = {instruction[30], func3};
                imm = 0;
                instType = R_Type;
                {rs2,rs1,rd} = {instruction[24:20],instruction[19:15],instruction[11:7]};  
                WEn=1'b1;
                ASel=0;
                BSel=0;
                PCSel=0;
                WBSel=WBALU;
            end
            // I type
            7'b0010011: begin
                // I* type
                if (func3==3'b101 | func3==3'b001) begin
                    aluSel = { instruction[30], func3};
                    imm =   {{28{instruction[24]}},instruction[23:20]};
                    instType =  I_Type; 
                end else  begin 
                    aluSel[2:0] = func3;
                    imm = { {21{instruction[31]}}, instruction[30:20]};
                    instType =  I_Type;
                end
                {rs2,rs1,rd} = {5'b0,instruction[19:15],instruction[11:7]};
                WEn=1'b1;
                ASel=0;
                BSel=1;
                PCSel=0;
                WBSel=WBALU;
            end
            // B type
            7'b1100011: begin
                aluSel[2:0] = func3;
                imm = { {20{instruction[31]}},instruction[7],instruction[30:25],instruction[11:8],1'b0};
                instType = B_Type;
                {rs2,rs1,rd} = {instruction[24:20],instruction[19:15],instruction[11:7]};  
                WEn=1'b1;
                ASel=1;
                BSel=1;
                PCSel=1;
                WBSel=WBNone;
            end
            // S type
            7'b0100011: begin
                aluSel[2:0] = func3;
                imm = { {21{instruction[31]}},instruction[30:25],instruction[11:7]};
                instType = S_Type;
                {rs2,rs1,rd} = {instruction[24:20],instruction[19:15],instruction[11:7]};  
                WEn=1'b1;
                ASel=0;
                BSel=1;
                PCSel=0;
                WBSel=WBNone;
            end
            
            // jal 
            7'b1101111: begin
                aluSel[2:0] = func3;
                imm = { {12{instruction[31]}},instruction[19:12],instruction[20],instruction[30:21],1'b0};
                instType = J_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,instruction[11:7]};
                WEn=1'b1;
                ASel=1;
                BSel=1;   
                PCSel=1;  
                WBSel=WBPc;  
                $display("jal");         
            end
            // jalr
            7'b1100111: begin
                // TODO: func3 000 not use
                aluSel[2:0] = func3;
                imm = { {12{instruction[31]}},instruction[19:12],instruction[20],instruction[30:21],1'b0};
                instType = J_Type;
                {rs2,rs1,rd} = {5'b0,instruction[19:15],instruction[11:7]};
                WEn=1'b1;
                ASel=0;
                BSel=1;  
                PCSel=1; 
                WBSel=WBPc;           
                $display("jalr");  
            end
            // auipc
            7'b0010111: begin
                aluSel = 4'b0;
                imm = { instruction[31:12],12'b0};
                instType = U_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,instruction[11:7]};
                WEn=1'b1;
                ASel=1;
                BSel=1;
                PCSel=0;
                WBSel=WBALU;
            end
            // lui
            7'b0110111: begin
                aluSel = 4'b0;
                imm = { instruction[31:12],12'b0};
                instType = U_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,instruction[11:7]};
                WEn=1'b1;
                ASel=0;
                BSel=1;
                PCSel=0;
                WBSel=WBALU;
            end
            // ebreak, ecall
            7'b1110011: begin
                aluSel[2:0] = func3;
                imm = { {21{instruction[30]}}, instruction[30:20]};
                instType =  E_Type;
                {rs2,rs1,rd} = {5'b0,instruction[19:15],instruction[11:7]};
                WEn=1'b0;
                ASel=0;
                BSel=1;
                PCSel=0;
                WBSel=WBNone;
            end
            default: begin
                aluSel = 4'b0;
                imm=0;
                instType = No_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,5'b0};
                WEn=1'b0;
                ASel=0;
                BSel=0;
                PCSel=0;
                WBSel=WBALU;
            end
        endcase
    end
endmodule // ysyx_24100012_inst_decode

