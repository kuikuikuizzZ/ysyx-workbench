#include <am.h>
#include <klib-macros.h>
#include <ysyxsoc.h>
#include <riscv.h>

extern char _heap_start;
extern char _data_start;
int main(const char *args);
void init_uart();
void bootloader();

extern char _pmem_start;
#define PMEM_SIZE (256 * 1024)
#define PMEM_END  ((uintptr_t)&_pmem_start + PMEM_SIZE)
#define npc_trap(code) asm volatile ("mv a0, %0; ebreak" : :"r"(code))

Area heap = RANGE(&_heap_start, &_heap_start+0x1000);
static const char mainargs[MAINARGS_MAX_LEN] = MAINARGS_PLACEHOLDER; // defined in CFLAGS

__attribute__ ((section(".bootutils"))) void putch(char ch) {
  // while(!(inb(UART_LSR)&0x20)) ;
  outb(UART_RBR,ch);
  return;
}

void halt(int code) {
  npc_trap(code);
  // should not reach here.
  while (1);
}

void _trm_init() {
  int ret = main(mainargs);
  halt(ret);
}
