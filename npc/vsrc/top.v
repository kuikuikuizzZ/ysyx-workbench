module ysyx_24100012_top (
  input clk,
  input rst,
  output io_halt
);
  parameter ADDR_WIDTH=32,DATA_WIDTH = 32;
  reg [DATA_WIDTH-1:0] inst;
  wire [DATA_WIDTH-1:0] imm;
  wire [3:0] alu_sel;
  wire [2:0] inst_type;
  wire [4:0] rs1,rs2,rd;
  wire [DATA_WIDTH-1:0] readData1,readData2,writeData;
  wire wen;
  ysyx_24100012_inst_fetch #(ADDR_WIDTH,DATA_WIDTH) ifu (clk,rst,inst);
  ysyx_24100012_inst_decode #(DATA_WIDTH)idu (inst,imm,alu_sel,inst_type,rs1,rs2,rd,wen);
  ysyx_24100012_regfiles #(ADDR_WIDTH,DATA_WIDTH,32,5) regfiles (clk,rst,writeData,rd,rs1,rs2,wen,readData1,readData2);
  ysyx_24100012_alu #(DATA_WIDTH,4)alu (clk,rst,readData1,imm,inst_type,alu_sel,writeData);
  assign io_halt = inst_type== 3'b110;
endmodule



module ysyx_24100012_inst_fetch #(ADDR_WIDTH,DATA_WIDTH) (
  input clk,
  input rst,
  output [DATA_WIDTH-1:0] inst
);
  parameter [ADDR_WIDTH-1:0] ORIGIN_ADDR=32'h80000000;
  reg [ADDR_WIDTH-1:0] pc;
  ysyx_24100012_rom  #(ADDR_WIDTH,DATA_WIDTH) mem (pc,inst);

  always@(posedge clk) begin
    if (rst== 1'b1) begin
      pc = ORIGIN_ADDR;
    end else begin
      pc = pc+4;
    end
  end
endmodule
