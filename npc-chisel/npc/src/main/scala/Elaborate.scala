object Elaborate extends App {
  val firtoolOptions = Array(
    "--mlir-print-ir-after-all ",
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket",
    ).reduce(_ + "," + _),
  )
  circt.stage.ChiselStage.emitSystemVerilogFile(
    new npc.Top(),args,firtoolOptions)
}
