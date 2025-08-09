#ifndef __NPC_H__
#define __NPC_H__

#include <klib-macros.h>

#if defined(__ARCH_X86_NPC)
# define DEVICE_BASE 0x0
#else
# define DEVICE_BASE 0xa0000000
#endif

#define MMIO_BASE     0xa0000000
#define FLASH_BASE    0x30000000 
#define UART_BASE     0x10000000 
#define KEYBOARD_BASE 0x10001000

#define UART_THR        (UART_BASE + 0x00)
#define UART_RBR        (UART_BASE + 0x00)
#define UART_IER        (UART_BASE + 0x01)
#define UART_FCR        (UART_BASE + 0x02)
#define UART_LCR        (UART_BASE + 0x03)
#define UART_LSR        (UART_BASE + 0x05)
#define UART_DLL         (UART_BASE + 0x00)
#define UART_DLM         (UART_BASE + 0x01)

// #define SERIAL_PORT     (DEVICE_BASE + 0x00003f8)
// #define KBD_ADDR        (DEVICE_BASE + 0x0000060)
#define RTC_ADDR        (DEVICE_BASE + 0x0000048)
#define VGACTL_ADDR     (DEVICE_BASE + 0x0000100)
#define AUDIO_ADDR      (DEVICE_BASE + 0x0000200)
// #define DISK_ADDR       (DEVICE_BASE + 0x0000300)
#define FB_ADDR         (MMIO_BASE   + 0x1000000)
// #define AUDIO_SBUF_ADDR (MMIO_BASE   + 0x1200000)

extern char _pmem_start;
#define PMEM_SIZE (256 * 1024)
#define PMEM_END  ((uintptr_t)&_pmem_start + PMEM_SIZE)
#define NPC_PADDR_SPACE \
  RANGE(&_pmem_start, PMEM_END), \
  RANGE(FB_ADDR, FB_ADDR + 0x200000), \
  RANGE(MMIO_BASE, MMIO_BASE + 0x1000) /* serial, rtc, screen, keyboard */

typedef uintptr_t PTE;

#define PGSIZE    4096

#endif
