import chisel3._
import circt.stage.ChiselStage

import npc._
import npc.common.{Config}
package sifive {
  package enterprise {
    package firrtl {

      case class NestedPrefixModulesAnnotation(
          val target: _root_.firrtl.annotations.Target,
          prefix: String,
          inclusive: Boolean
      ) extends _root_.firrtl.annotations.SingleTargetAnnotation[
            _root_.firrtl.annotations.Target
          ] {

        override def duplicate(n: _root_.firrtl.annotations.Target) = ???
      }
    }
  }
}


class ysyx_24100012 extends Module { 
  implicit val conf = Config()
  val io = IO(new CoreIo())
  val core = Module(new Core())
  chisel3.experimental.annotate(
    new chisel3.experimental.ChiselAnnotation {
      override def toFirrtl = sifive.enterprise.firrtl
        .NestedPrefixModulesAnnotation(core.toTarget, "ysyx_24100012_", true)
    }
  )
  core.io <> io
}

object Elaborate extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
       "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket",
    ).reduce(_ + "," + _),
  )
  emitVerilog(
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
