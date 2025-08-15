module ysyx_24100012_LFSR (
    input clk,
    input rst_n,
    input enable,
    input [3:0] seed,
    output reg[3:0] random
); 

    reg [3:0] lfsr_reg;
    reg feedback;
    always @(posedge clk) begin
        if (rst_n) begin
            lfsr_reg = seed;
        end else if (enable) begin
            feedback = lfsr_reg[3] ^ lfsr_reg[2];
            lfsr_reg = {lfsr_reg[2:0], feedback};
        end else begin
            lfsr_reg = lfsr_reg;
        end
    end
    
    assign  random = lfsr_reg; 
endmodule