module shifter8(
  input  [7:0] seed,
  input clk,
  input rst,
  output reg [7:0] Result
);
  reg newbit;
  integer i;
  reg [7:0] state;
  always@(posedge clk) begin
    if (rst) begin
      state <= seed;
    end
    else begin
        newbit = state[4]^state[3]^state[2]^state[0];
        for(i=0;i<7;i++) begin
          state[i] <= state[i+1];
        end
        state[7] <= newbit;
    end
  end

  assign Result = state;
  
endmodule

