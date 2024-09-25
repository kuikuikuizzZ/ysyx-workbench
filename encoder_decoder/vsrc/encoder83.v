module encoder83(
  input  [7:0] x,
  input  en,
  input clk,
  output reg [2:0]y,
  output flag
);
  integer i;
  always @(x or en) begin
    if (en) begin
      y = 0;
      flag=1;
      for( i = 0; i <= 7; i = i+1)
          if(x[i] == 1)  y = i[2:0];
    end
    else  begin 
      y = 0;
      flag=0;
    end
  end
endmodule

