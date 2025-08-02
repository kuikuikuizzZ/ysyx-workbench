#include <am.h>
#include <riscv.h>
#include <ysyxsoc.h>

__attribute__ ((section(".bootutils"))) void init_uart() {
    
    outb(UART_LCR, 0b10000011); 
    // set baud rate
    outb(UART_DLM, 0x00);
    outb(UART_DLL, 0x01);
    // 8bits、1stop bit
    outb(UART_LCR, 0x03); 
    // outb(UART_FCR, 0x01); 

}