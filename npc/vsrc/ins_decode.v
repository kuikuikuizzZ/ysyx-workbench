module ysyx_24100012_inst_decode #(WIDTH) (
    input [WIDTH-1:0] instruction,
    output [WIDTH-1:0]  imm ,
    output [3:0]        alu_sel,
    output [2:0]        inst_type,
)
    parameter [2:0] R_Type=3'b000,I_Type=3'b001,B_Type=3'b010, S_Type=3'b100;
    parameter [2:0] J_Type=3'b011,U_Type=3'b101, E_Type = 3'b110;

    wire [6:0] opcode = instruction[6:0];
    wire [3:0] func3  = instruction[14:12];
    
    always @ (*) begin
        case (opcode)
            // R type
            7'b0110011: begin
                alu_sel = {instruction[30], func3};
                imm = 0;
                inst_type = R_Type
            end
            // I type
            7'b0010011: begin
                if (funn3==3'b101 | funn3==3'b001) begin
                    alu_sel = { instruction[30], func3};
                    imm[4:0] =   instruction[24:20];
                    inst_type =  I_Type; 
                end else  begin 
                    alu_sel = func3;
                    imm = { 21{instruction[30]}, instruction[30:20]};
                    inst_type =  I_Type;
                end 
            end
            // B type
            7'b1100011: begin
                alu_sel[2:0] = func3;
                imm[4:1] = { instruction[31],instruction[0],instruction[30:25],instruction[11:8],{0}};
                inst_type = B_Type;
            end
            // S type
            7'b0100011: begin
                alu_sel[2:0] = func3;
                imm[4:1] = { 21{instruction[31]},instruction[30:25],instruction[11:7]};
                inst_type = S_Type;
            end
            // J type
            7'b1101111: begin
                alu_sel[2:0] = func3;
                imm[4:1] = { 12{instruction[31]},instruction[19:12],instruction[20],instruction[30:21],{0}};
                inst_type = J_Type;
            end
            // U type
            7'b0010111:
            7'b0110111: begin
                alu_sel = 0;
                imm = { instruction[31:12],12{0}};
                inst_type = U_Type;
            end
            7'b1110011: begin
                alu_sel = func3;
                imm = { 21{instruction[30]}, instruction[30:20]};
                inst_type =  I_Type;
            end
            default:
        endcase
    end
endmodule


module  ysyx_24100012_imm_gen  #(WIDTH)(
    input [WIDTH-1:0] instruction,
    output [WIDTH-1:0]  imm ,
    
);
    
endmodule //moduleName
