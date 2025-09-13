module main ();
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
  always #10000 clk = ~clk;

  integer i;
  integer j;
  initial begin
    for (j =0; j <= 10 ; j = j + 1) begin
      reset = 1;
    end
    for (i = 0; i <= 100000; i = i + 1) begin
      reset = 0;
      if (externalPins_halt) begin
          $display("Test completed after %d iterations", i);
          $finish;
      end
    end
    $display("Test completed after %d iterations", i);
    $finish;
  end

  assign externalPins_ps2_clk = 1'b0;
  assign externalPins_ps2_data = 1'b0;
  assign externalPins_gpio_in = 16'b0;
  assign externalPins_uart_rx = 1'b0;

endmodule // main