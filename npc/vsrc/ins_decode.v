module ysyx_24100012_inst_decode #(DATA_WIDTH) (
    input [DATA_WIDTH-1:0] instruction,
    output reg [DATA_WIDTH-1:0]  imm ,
    output reg [3:0]        alu_sel,
    output reg [2:0]        inst_type,
    output reg [4:0]        rs1,
    output reg [4:0]        rs2,
    output reg [4:0]        rd,
    output reg              wen
);
    parameter [2:0] R_Type=3'b000,I_Type=3'b001,B_Type=3'b010, S_Type=3'b100;
    parameter [2:0] J_Type=3'b011,U_Type=3'b101, E_Type = 3'b110, No_Type=3'b111;

    wire [6:0] opcode; 
    wire [2:0] func3;
    assign opcode = instruction[6:0];
    assign func3  = instruction[14:12];
    always @ (*) begin
        case (opcode)
            // R type
            7'b0110011: begin
                alu_sel = {instruction[30], func3};
                imm = 0;
                inst_type = R_Type;
                {rs2,rs1,rd} = {instruction[24:20],instruction[19:15],instruction[11:7]};  
                wen=1'b1;
            end
            // I type
            7'b0010011: begin
                // I* type
                if (func3==3'b101 | func3==3'b001) begin
                    alu_sel = { instruction[30], func3};
                    imm =   {{28{instruction[24]}},instruction[23:20]};
                    inst_type =  I_Type; 
                end else  begin 
                    alu_sel[2:0] = func3;
                    imm = { {21{instruction[31]}}, instruction[30:20]};
                    inst_type =  I_Type;
                end
                {rs2,rs1,rd} = {5'b0,instruction[19:15],instruction[11:7]};
                wen=1'b1;
            end
            // B type
            7'b1100011: begin
                alu_sel[2:0] = func3;
                imm = { {20{instruction[31]}},instruction[7],instruction[30:25],instruction[11:8],1'b0};
                inst_type = B_Type;
                {rs2,rs1,rd} = {instruction[24:20],instruction[19:15],instruction[11:7]};  
                wen=1'b1;
            end
            // S type
            7'b0100011: begin
                alu_sel[2:0] = func3;
                imm = { {21{instruction[31]}},instruction[30:25],instruction[11:7]};
                inst_type = S_Type;
                {rs2,rs1,rd} = {instruction[24:20],instruction[19:15],instruction[11:7]};  
                wen=1'b1;
            end
            // J type
            7'b1101111: begin
                alu_sel[2:0] = func3;
                imm = { {12{instruction[31]}},instruction[19:12],instruction[20],instruction[30:21],1'b0};
                inst_type = J_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,5'b0};
                wen=1'b0;
            end
            // U type
            7'b0010111,7'b0110111: begin
                alu_sel = 4'b0;
                imm = { instruction[31:12],12'b0};
                inst_type = U_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,5'b0};
                wen=1'b0;
            end
            // ebreak, ecall
            7'b1110011: begin
                alu_sel[2:0] = func3;
                imm = { {21{instruction[30]}}, instruction[30:20]};
                inst_type =  E_Type;
                {rs2,rs1,rd} = {5'b0,instruction[19:15],instruction[11:7]};
                wen=1'b0;
            end
            default: begin
                alu_sel = 4'b0;
                imm=0;
                inst_type = No_Type;
                {rs2,rs1,rd} = {5'b0,5'b0,5'b0};
                wen=1'b0;
            end
        endcase
    end
endmodule // ysyx_24100012_inst_decode

