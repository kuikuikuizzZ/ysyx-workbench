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
    
  }


void __am_uart_tx(AM_UART_TX_T *send) {
  outb(UART_THR, send->data);
} 
 

void __am_uart_rx(AM_UART_RX_T *recv) {
  recv->data = (inb(UART_LSR) & 0x01) ? inb(UART_RBR) : -1;
}
