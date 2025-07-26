#include <am.h>
#include <riscv.h>
#include <ysyxsoc.h>

void init_uart() {
    // outb(UART_IER, 0x00);
    
    outb(UART_LCR, 0xf0); 
    // uint16_t baud_rate = (uint16_t)115200;
    // uint16_t divisor = 1843200 / (16 * baud_rate); 
    // outb(UART_DLM, (divisor >> 8) & 0xFF);
    // outb(UART_DLL, divisor & 0xFF);
    outb(UART_DLM, 0x00);
    outb(UART_DLL, 0x01);
    // 8bits、1stop bit
    outb(UART_LCR, 0x03); 
    outb(UART_FCR, 0x01); 


    // default LCR 00000011b
    // char lcr = 0b10000011;
    // outb(UART_LCR, lcr);
    // // set baud rate
    // outb(UART_IER, 0x0);
    // outb(UART_THR, 0x01);
    // lcr = 0b00000011;
    // outb(UART_LCR,lcr);
}