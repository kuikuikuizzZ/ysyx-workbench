import chisel3._
import npc._

object Elaborate extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket",
    ).reduce(_ + "," + _),
    "-ll 2",
  )
  circt.stage.ChiselStage.emitSystemVerilogFile(
    new ysyx_24100012(),
    args,
    firtoolOptions)
}


// object Elaborate extends App {
//   val firtoolOptions = Array(
//     "--lowering-options=" + List(
//       // make yosys happy
//       // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
//       "disallowLocalVariables",
//       "disallowPackedArrays",
//       "locationInfoStyle=wrapInAtSquareBracket",
//     ).reduce(_ + "," + _),
//   )
//   circt.stage.ChiselStage.emitSystemVerilogFile(
//     new ysyxSoCFull(),
//     args,
//     firtoolOptions)
// }
