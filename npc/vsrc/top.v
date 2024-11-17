module top #(WIDTH)(
  input clk,
  input rst,
)
  ysyx_24100012_Reg #(WIDTH,RESET_VAL=0x80000000) pc; 

  ysyx_24100012_regfiles #(WIDTH) regfiles ;

  assign pc = pc+4; 
endmodule