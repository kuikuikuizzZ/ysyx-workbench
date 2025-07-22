#include <am.h>
#include <klib-macros.h>
#include <ysyxsoc.h>
#include <riscv.h>

extern char _heap_start;
extern char _data_start;
extern char _lma_data_start,_data_start, _data_end,_bss_start, _bss_end;
int main(const char *args);
void init_uart();
extern char _pmem_start;
#define PMEM_SIZE (256 * 1024)
#define PMEM_END  ((uintptr_t)&_pmem_start + PMEM_SIZE)
#define npc_trap(code) asm volatile ("mv a0, %0; ebreak" : :"r"(code))

Area heap = RANGE(&_heap_start, &_heap_start+0x1000);
static const char mainargs[MAINARGS_MAX_LEN] = MAINARGS_PLACEHOLDER; // defined in CFLAGS



void putch(char ch) {
  while(!(inb(UART_LSR)&0x20)) ;
  outb(UART_RBR,ch);
  return;
}

void halt(int code) {
  npc_trap(code);

  // should not reach here.
  while (1);
}

void _trm_init() {
  char *src = &_lma_data_start;
  char *dst = &_data_start;
  while (dst < &_data_end) {
    *dst++ = *src++;
  }
  for (dst = &_bss_start; dst < &_bss_end; dst++)
    *dst = 0;
  init_uart();
  int ret = main(mainargs);
  halt(ret);
}
