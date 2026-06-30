package npc.galois

import chisel3._
import chisel3.util._
import npc._
import npc.common._
import npc.galois._
import npc.galois.Constants._

class GaloisSimFrontFetchHelper(implicit val conf: Config) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())

    val updatePtrCount = Input(UInt(32.W))

    val redirect = Input(Bool())
    val redirectPc = Input(UInt(conf.xprlen.W))
    val redirectTarget = Input(UInt(conf.xprlen.W))
    val redirectType = Input(UInt(32.W))

    val retireValid = Input(Bool())
    val retirePc = Input(UInt(conf.xprlen.W))

    val out0Valid = Output(Bool())
    val out0Pc = Output(UInt(conf.xprlen.W))
    val out0Inst = Output(UInt(conf.xlen.W))
    val out0PreDecode = Output(UInt(32.W))

    val out1Valid = Output(Bool())
    val out1Pc = Output(UInt(conf.xprlen.W))
    val out1Inst = Output(UInt(conf.xlen.W))
    val out1PreDecode = Output(UInt(32.W))
  })

  setInline(
    "GaloisSimFrontFetchHelper.v",
    """
import "DPI-C" function void GaloisSimFrontFetch(
  input int offset,
  output int valid,
  output int pc,
  output int instr,
  output int preDecode
);

import "DPI-C" function void GaloisSimFrontUpdatePtr(
  input int updateCount
);

import "DPI-C" function void GaloisSimFrontRedirect(
  input int redirectValid,
  input int redirectPc,
  input int redirectTarget,
  input int redirectType
);

import "DPI-C" function void GaloisSimFrontRobCommit(
  input int valid,
  input int pc
);

module GaloisSimFrontFetchHelper(
  input clock,
  input reset,
  input [31:0] updatePtrCount,
  input redirect,
  input [31:0] redirectPc,
  input [31:0] redirectTarget,
  input [31:0] redirectType,
  input retireValid,
  input [31:0] retirePc,
  output reg out0Valid,
  output reg [31:0] out0Pc,
  output reg [31:0] out0Inst,
  output reg [31:0] out0PreDecode,
  output reg out1Valid,
  output reg [31:0] out1Pc,
  output reg [31:0] out1Inst,
  output reg [31:0] out1PreDecode
);
  integer out0ValidInt;
  integer out1ValidInt;

  always @(posedge clock) begin
    if (reset) begin
      out0Valid <= 1'b0;
      out0Pc <= 32'b0;
      out0Inst <= 32'b0;
      out0PreDecode <= 32'b0;
      out1Valid <= 1'b0;
      out1Pc <= 32'b0;
      out1Inst <= 32'b0;
      out1PreDecode <= 32'b0;
    end else begin
      GaloisSimFrontUpdatePtr(updatePtrCount);
      GaloisSimFrontRedirect({31'b0, redirect}, redirectPc, redirectTarget, redirectType);
      GaloisSimFrontRobCommit({31'b0, retireValid}, retirePc);

      GaloisSimFrontFetch(0, out0ValidInt, out0Pc, out0Inst, out0PreDecode);
      GaloisSimFrontFetch(1, out1ValidInt, out1Pc, out1Inst, out1PreDecode);
      out0Valid <= out0ValidInt[0];
      out1Valid <= out1ValidInt[0];
    end
  end
endmodule
"""
  )
}

class SimFrontend(implicit conf: Config) extends IFUModule {
  io := DontCare
  val helper = Module(new GaloisSimFrontFetchHelper())
  helper.io.clock := clock
  helper.io.reset := reset.asBool

  val fetchValidCount = helper.io.out0Valid.asUInt + helper.io.out1Valid.asUInt
  helper.io.updatePtrCount := Mux(io.ifu_dec.fire && !io.redirect, fetchValidCount.pad(32), 0.U(32.W))
  helper.io.redirect := io.redirect
  helper.io.redirectPc := io.retireA.pc
  helper.io.redirectTarget := io.retireA.target
  helper.io.redirectType := io.retireA.bju_out.redirect_type.pad(32)
  helper.io.retireValid := io.retireA.valid
  helper.io.retirePc := io.retireA.pc

  io.ifu_dec.valid := !io.redirect && helper.io.out0Valid
  io.ifu_dec.bits.instA.pc := helper.io.out0Pc
  io.ifu_dec.bits.instA.inst := Mux(helper.io.out0Valid, helper.io.out0Inst, BUBBLE)
  io.ifu_dec.bits.instB.pc := helper.io.out1Pc
  io.ifu_dec.bits.instB.inst := Mux(helper.io.out1Valid, helper.io.out1Inst, BUBBLE)
  io.ifu_dec.bits.exception := EXC_NORMAL
  io.ifu_dec.bits.bpu_resp := 0.U.asTypeOf(new BPUResp())

  val out0Step = Mux(helper.io.out0PreDecode(1), 2.U(conf.xprlen.W), 4.U(conf.xprlen.W))
  io.debug.pc := Mux(helper.io.out0Valid, helper.io.out0Pc, conf.START_ADDR)
  io.debug.pc_next := Mux(helper.io.out1Valid, helper.io.out1Pc, helper.io.out0Pc + out0Step)

  io.axi_bus.req.valid := false.B
  io.axi_bus.req.bits := DontCare
  io.axi_bus.resp.ready := true.B
}
