#ifndef __CONFIG_H__
#define __CONFIG_H__

#define CONFIG_ITRACE
#define CONFIG_SDB
// #define CONFIG_DIFFTEST
#define CONFIG_RVE          // use riscv32e
// #define CONFIG_WATCH_TOP

#define CONFIG_DEVICE
#define CONFIG_SERIAL_MMIO 0xa00003f8
#define CONFIG_HAS_SERIAL
#define CONFIG_RTC_MMIO 0xa0000048
#define CONFIG_HAS_TIMER

#define CONFIG_TIMER_CLOCK_GETTIME

#define CONFIG_VGA_SHOW_SCREEN
#define CONFIG_HAS_VGA
#define CONFIG_VGA_CTL_MMIO 0xa0000100
#define CONFIG_FB_ADDR 0xa1000000

#define CONFIG_MBASE 0x80000000
#define CONFIG_MSIZE 0x8000000
#endif