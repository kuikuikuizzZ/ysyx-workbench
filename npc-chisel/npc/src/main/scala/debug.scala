
package npc

import chisel3._
import chisel3.util._
import npc.common._
import npc._
import npc.devices._


class debug_port() extends BlackBox with HasBlackBoxInline{ 
    val io = IO(new Bundle {
        val clock = Input(Clock())
        val reset = Input(Bool())   
        val halt = Input(Bool())
        val pc = Input(UInt(32.W))
        val inst = Input(UInt(32.W))
     })

     setInline("debug_port.v",
     """
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
     """.stripMargin
     )

}