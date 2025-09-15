
import chisel3._
import chisel3.util._
import chisel3.experimental.{annotate, ChiselAnnotation}
import firrtl.annotations.{MemorySynthInit, MemoryFileAnnotation}

/**
 * 支持初始化的同步读取内存
 * @param size 内存大小
 * @param dataWidth 数据位宽
 * @param initFile 初始化文件路径（可选）
 */
class InitializableSyncMem(size: Int, dataWidth: Int, initFile: Option[String] = None) extends Module {
  val io = IO(new Bundle {
    val raddr = Input(UInt(log2Ceil(size).W))
    val rdata = Output(UInt(dataWidth.W))
    val waddr = Input(UInt(log2Ceil(size).W))
    val wdata = Input(UInt(dataWidth.W))
    val wen   = Input(Bool())
  })
  
  // 1. 创建内存
  val mem = SyncReadMem(size, UInt(dataWidth.W))
  
  // 2. 添加内存初始化注解
  annotate(new ChiselAnnotation {
    override def toFirrtl = MemorySynthInit
  })
  
  // 3. 添加初始化文件注解（如果提供）
  initFile.foreach { file =>
    annotate(new ChiselAnnotation {
      override def toFirrtl = MemoryFileAnnotation(file, file)
    })
  }
  
  // 4. 读端口
  io.rdata := mem.read(io.raddr)
  
  // 5. 写端口
  when(io.wen) {
    mem.write(io.waddr, io.wdata)
  }
  
  // 6. 复位初始化逻辑（可选）
  if (initFile.isEmpty) {
    when(reset.asBool) {
      // 默认初始化：将所有位置为0
      for (i <- 0 until size) {
        mem.write(i.U, 0.U(dataWidth.W))
      }
    }
  }
}

object InitializableSyncMem {
  /**
   * 创建内存并生成初始化文件
   * @param size 内存大小
   * @param dataWidth 数据位宽
   * @param initValues 初始化值序列
   */
  def apply(size: Int, dataWidth: Int, initValues: Seq[BigInt]): InitializableSyncMem = {
    // 生成初始化文件
    val fileName = s"mem_init_${size}x${dataWidth}.hex"
    val content = initValues.map(v => f"$v%0${dataWidth/4}X").mkString("\n")
    Files.write(Paths.get(fileName), content.getBytes(StandardCharsets.UTF_8))
    
    // 创建内存实例
    Module(new InitializableSyncMem(size, dataWidth, Some(fileName)))
  }
  
  /**
   * 创建全零初始化的内存
   */
  def zeroInit(size: Int, dataWidth: Int): InitializableSyncMem = {
    Module(new InitializableSyncMem(size, dataWidth, None))
  }
}