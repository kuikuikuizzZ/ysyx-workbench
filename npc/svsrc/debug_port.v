
     import "DPI-C" function void dpi_port(input int halt, input int pc, input int inst);
     import "DPI-C" function void lsu_port(input enable,  input fcn, input lsu_port_typ,input int addr, input int data);

     module debug_port(
        input clock,
        input reset,
        input halt, 
        input [31:0] pc,
        input [31:0] inst,
        input [31:0] lsu_port_addr,
        input [31:0] lsu_port_rdata,
        input [31:0] lsu_port_wdata,
        input lsu_port_mem_en,
        input lsu_port_fcn,
        input lsu_port_valid,
        input [1:0]  lsu_port_typ
        );

        wire [31:0] expand_halt = {31'b0,halt};
        wire [31:0] expand_typ   = {31'b0,lsu_port_typ};
        always @(posedge clock) begin
            dpi_port(expand_halt, pc, inst);
        end

        always @(posedge clock) begin
            if (lsu_port_mem_en && lsu_port_fcn == 1'b1) begin
                lsu_port(lsu_port_mem_en,lsu_port_fcn,expand_typ, lsu_port_addr, lsu_port_wdata);
            end else if (lsu_port_valid && lsu_port_fcn == 1'b0) begin
                lsu_port(lsu_port_valid,lsu_port_fcn,expand_typ, lsu_port_addr, lsu_port_rdata);
            end else begin
                lsu_port(1'd0,1'd0,32'd0,32'd0,32'd0);
            end
        end
     endmodule
     
