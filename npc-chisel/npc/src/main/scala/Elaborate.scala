import chisel3._
import npc.Top
import circt.stage.FirtoolOption

object Elaborate extends App {
  val firtoolOptions = Seq(
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket",
    ).reduce(_ + "," + _),
    FirtoolOption("--split-verilog"),
  )
  circt.stage.ChiselStage.emitSystemVerilogFile(
    new Top(),
    args,
    firtoolOptions)
}
