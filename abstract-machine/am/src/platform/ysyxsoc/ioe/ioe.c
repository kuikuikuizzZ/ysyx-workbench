#include <am.h>
#include <klib-macros.h>
#include <klib.h>
void __am_timer_init();
void __am_gpu_init();
void __am_audio_init();

void __am_timer_rtc(AM_TIMER_RTC_T *);
void __am_timer_uptime(AM_TIMER_UPTIME_T *);
void __am_input_keybrd(AM_INPUT_KEYBRD_T *);
void __am_gpu_config(AM_GPU_CONFIG_T *);
void __am_gpu_status(AM_GPU_STATUS_T *);
void __am_gpu_fbdraw(AM_GPU_FBDRAW_T *);
void __am_audio_config(AM_AUDIO_CONFIG_T *);
void __am_audio_ctrl(AM_AUDIO_CTRL_T *);
void __am_audio_status(AM_AUDIO_STATUS_T *);
void __am_audio_play(AM_AUDIO_PLAY_T *);
void __am_uart_rx(AM_UART_RX_T *recv);
void __am_uart_tx(AM_UART_TX_T *send);

static void __am_timer_config(AM_TIMER_CONFIG_T *cfg) { cfg->present = true; cfg->has_rtc = false; }
static void __am_input_config(AM_INPUT_CONFIG_T *cfg) { cfg->present = true;  }
static void __am_uart_config(AM_INPUT_CONFIG_T *cfg) { cfg->present = false;  }

typedef void (*handler_t)(void *buf) ; 

static handler_t lut[128];

static void fail(void *buf) { panic("access nonexist register"); }

bool ioe_init() {
  lut[AM_TIMER_CONFIG] = (handler_t)__am_timer_config,
  lut[AM_TIMER_RTC   ] = (handler_t)__am_timer_rtc,
  lut[AM_TIMER_UPTIME] = (handler_t)__am_timer_uptime,
  lut[AM_INPUT_CONFIG] = (handler_t)__am_input_config,
  lut[AM_INPUT_KEYBRD] = (handler_t)__am_input_keybrd,
  lut[AM_UART_CONFIG]  = (handler_t)__am_uart_config,
  lut[AM_GPU_CONFIG  ] = (handler_t)__am_gpu_config,
  lut[AM_GPU_FBDRAW  ] = (handler_t)__am_gpu_fbdraw,
  lut[AM_GPU_STATUS  ] = (handler_t)__am_gpu_status,
  lut[AM_AUDIO_CONFIG] = (handler_t)__am_audio_config,
  lut[AM_AUDIO_CTRL  ] = (handler_t)__am_audio_ctrl,
  lut[AM_AUDIO_STATUS] = (handler_t)__am_audio_status,
  lut[AM_AUDIO_PLAY  ] = (handler_t)__am_audio_play,
  lut[AM_UART_TX     ] = (handler_t)__am_uart_tx,
  lut[AM_UART_RX     ] = (handler_t)__am_uart_rx;
  
  for (int i = 0; i < LENGTH(lut); i++)
  if (!lut[i]) lut[i] = fail;

  __am_timer_init();
  // __am_gpu_init();
  // __am_audio_init();
  printf("%d %d\n",AM_TIMER_CONFIG,AM_TIMER_UPTIME);
  return true;
}

void ioe_read (int reg, void *buf) { 
  void (*handler_t)(void *) = lut[reg];
  handler_t(buf);
}
void ioe_write(int reg, void *buf) { ((handler_t)lut[reg])(buf); }
