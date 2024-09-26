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
      newbit = state[4]^state[3]^state[2]^state[0];
      for(i=0;i<7;i++) begin
        Result[7-i] = state[7-i-1];
      end
      Result[0] = newbit;
    end
  always@(*) begin
    if (rst) 
      state = seed;
    else
      state = Result;
  end
endmodule

