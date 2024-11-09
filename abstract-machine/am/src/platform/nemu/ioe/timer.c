#include <am.h>
#include <nemu.h>

long long rtc[2];
static long long  start=0; 
static long long  now=0; 

void __am_timer_init() {
  ioe_read(RTC_ADDR,&rtc[0]);
  ioe_read(RTC_ADDR+4,&rtc[1]);
  start = (rtc[1]<<32)+rtc[0];
}

void __am_timer_uptime(AM_TIMER_UPTIME_T *uptime) {
  ioe_read(RTC_ADDR,&rtc[0]);
  ioe_read(RTC_ADDR+4,&rtc[1]);
  now = (rtc[1]<<32)+rtc[0];
  uptime->us = now-start;
}

void __am_timer_rtc(AM_TIMER_RTC_T *rtc) {
  rtc->second = 0;
  rtc->minute = 0;
  rtc->hour   = 0;
  rtc->day    = 0;
  rtc->month  = 0;
  rtc->year   = 1900;
}
