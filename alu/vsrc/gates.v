module gates(
  input  [3:0] A,
  input  [3:0] B,
  input  [2:0] op,
  output reg [3:0] Result,
  output reg Zero
);
  always@(*)
  case(op)
  3'b010: Result = ~A;
  3'b011: Result = A&B;
  3'b100: Result = A|B;
  3'b101: Result = A^B;
  default: Result = 0;
  endcase
  assign Zero = (Result==0)?1:0;
endmodule

