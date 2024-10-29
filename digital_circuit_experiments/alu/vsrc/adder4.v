module adder4(
  input  [3:0] A,
  input  [3:0] B,
  input   Cin,
  output reg [3:0]Result,
  output Carry,
  output Overflow,
  output Zero
);
  wire [3:0] t_add_Cin,temp_cin;
  assign temp_cin[0] = Cin;
  assign t_add_Cin =( {4{Cin}}^B )+temp_cin;  
  assign { Carry, Result } = A + t_add_Cin;
  assign Overflow = (A[3] == t_add_Cin[3]) & (Result [3] != A[3]); 
  assign Zero = ~(| Result);
endmodule

