
     import "DPI-C" function void dpi_port(input int halt, input int pc, input int inst);
     module debug_port(
        input clock,
        input reset,
        input halt, 
        input [31:0] pc,
        input [31:0] inst);
        wire [31:0] expand_halt = {31'b0,halt};
        always @(posedge clock) begin
            dpi_port(expand_halt, pc, inst);
        end
     endmodule
     
