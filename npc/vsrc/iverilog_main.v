`timescale 1ns/1ps  // 时间单位/时间精度
module main ();
  localparam CLK_PERIOD = 1000000;
  reg clk, reset;

  wire [15:0]   externalPins_gpio_out;	
  wire [15:0]   externalPins_gpio_in;	// 
  wire [7:0]    externalPins_gpio_seg_0,
                externalPins_gpio_seg_1,
                externalPins_gpio_seg_2,
                externalPins_gpio_seg_3,
                externalPins_gpio_seg_4,
                externalPins_gpio_seg_5,
                externalPins_gpio_seg_6,
                externalPins_gpio_seg_7;
  wire          externalPins_ps2_clk,	// 
                externalPins_ps2_data;	
  wire [7:0]    externalPins_vga_r,	// ho
                externalPins_vga_g,	// ho
                externalPins_vga_b;	// ho
  wire          externalPins_vga_hsync,	
                externalPins_vga_vsync,	
                externalPins_vga_valid;	
  wire          externalPins_uart_tx,	
                externalPins_uart_rx, 
                externalPins_halt	;
  ysyxSoCFull dut (
      .clock(clk),	// home/uenui/code
      .reset(reset),	// home/uenui/code
      .externalPins_gpio_out    (externalPins_gpio_out),	  
      .externalPins_gpio_in     (externalPins_gpio_in),	   
      .externalPins_gpio_seg_0	(externalPins_gpio_seg_0),
      .externalPins_gpio_seg_1	(externalPins_gpio_seg_1),
      .externalPins_gpio_seg_2	(externalPins_gpio_seg_2),
      .externalPins_gpio_seg_3	(externalPins_gpio_seg_3),
      .externalPins_gpio_seg_4	(externalPins_gpio_seg_4),
      .externalPins_gpio_seg_5	(externalPins_gpio_seg_5),
      .externalPins_gpio_seg_6	(externalPins_gpio_seg_6),
      .externalPins_gpio_seg_7	(externalPins_gpio_seg_7),
      .externalPins_ps2_clk     (externalPins_ps2_clk)	,	
      .externalPins_ps2_data    (externalPins_ps2_data) ,	
      .externalPins_vga_r       (externalPins_vga_r)    ,	
      .externalPins_vga_g       (externalPins_vga_g)    ,	
      .externalPins_vga_b       (externalPins_vga_b)    ,	
      .externalPins_vga_hsync   (externalPins_vga_hsync),	
      .externalPins_vga_vsync   (externalPins_vga_vsync),	
      .externalPins_vga_valid   (externalPins_vga_valid),	
      .externalPins_uart_rx     (externalPins_uart_tx)	,	
      .externalPins_uart_tx     (externalPins_uart_rx) ,	
      .externalPins_halt	      (externalPins_halt)	// 
  );
    reg [31:0] cycle_count = 0; // 周期计数器

    // 3. 时钟生成器 (10ns周期 = 100MHz)
    initial begin
        clk = 1; // 初始时钟为0
        forever #1 clk = ~clk; // 每5ns翻转一次，产生10ns周期
    end
    
    // 4. 复位控制
    initial begin
        reset = 1; // 初始复位有效
        #1;       // 保持20ns（2个时钟周期）
        reset = 0; // 释放复位
    end

    reg [31:0] a0;
    wire halt = dut.asic.cpu.cpu.core.lsu.io_mem_wb_bits_ebreak;
    // 5. 周期计数器
    always @(posedge clk) begin
        if (reset) begin
            cycle_count <= 0; // 复位时清零
        end else begin
            cycle_count <= cycle_count + 1; // 每个时钟上升沿计数
        end
        if (halt) begin
          a0 <= dut.asic.cpu.cpu.core.reg_file.regfile_mem_ext.Memory[10];
        end
    end

  initial begin
  // 初始化VCD波形文件
  $dumpfile("wave.vcd");
  $dumpvars(0, dut); // 记录所有信号
  
  
  // 等待复位释放
  wait(reset == 0);
  $display("Reset released at time %t", $time);
  
  // 主仿真循环
  fork
    // 分支1: 周期计数监控
    begin
      while (cycle_count < CLK_PERIOD) begin
        @(posedge clk);
        cycle_count = cycle_count + 1;
      end
      $display("Completed %d cycles at time %t", cycle_count,$time);
    end
    
    // 分支2: Halt信号监控
    begin
      wait(externalPins_halt == 1);
      $display("Halt signal detected at time %t", $time);
    end
  join_any // 任意一个条件满足即继续
  
  // 结束仿真
  #2; // 额外等待一个周期
  $display("Simulation completed");
  
  // 报告结束原因
  if (externalPins_halt && !a0) begin
    $display("GOOD! Terminated by HALT signal after %d cycles a0 %x",cycle_count,a0 );
  end else if (externalPins_halt) begin
    $display("BAD! Terminated by HALT signal after %d cycles a0 %x",cycle_count,a0);
  end else begin
    $display("Terminated after completing %d cycles a0 %x",cycle_count,a0);
  end
  
  $finish;
end

  assign externalPins_ps2_clk = 1'b0;
  assign externalPins_ps2_data = 1'b0;
  assign externalPins_gpio_in = 16'b0;
  assign externalPins_uart_rx = 1'b0;

endmodule // main