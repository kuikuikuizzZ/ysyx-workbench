#include <am.h>
#include <nemu.h>
#include <klib.h>
#define AUDIO_FREQ_ADDR      (AUDIO_ADDR + 0x00)
#define AUDIO_CHANNELS_ADDR  (AUDIO_ADDR + 0x04)
#define AUDIO_SAMPLES_ADDR   (AUDIO_ADDR + 0x08)
#define AUDIO_SBUF_SIZE_ADDR (AUDIO_ADDR + 0x0c)
#define AUDIO_INIT_ADDR      (AUDIO_ADDR + 0x10)
#define AUDIO_COUNT_ADDR     (AUDIO_ADDR + 0x14)

static volatile int pos = 0;

void __am_audio_init() {

}

void __am_audio_config(AM_AUDIO_CONFIG_T *cfg) {
  cfg->present = true;
}

void __am_audio_ctrl(AM_AUDIO_CTRL_T *ctrl) {
  outl(AUDIO_FREQ_ADDR,ctrl->freq);
  outl(AUDIO_CHANNELS_ADDR, ctrl->channels);
  outl(AUDIO_SAMPLES_ADDR, ctrl->samples);
  outl(AUDIO_COUNT_ADDR, 0);
}

void __am_audio_status(AM_AUDIO_STATUS_T *stat) {
  stat->count = inl(AUDIO_COUNT_ADDR);
}

void __am_audio_play(AM_AUDIO_PLAY_T *ctl) {
  uint32_t len = ctl->buf.end - ctl->buf.start;
  uint32_t nwrite = 0;
  if (len <= 0) {
    return; // nothing to play
  }
  if (pos + len > inl(AUDIO_SBUF_SIZE_ADDR)) {
    while (inl(AUDIO_COUNT_ADDR) > 0) {
      // wait until the audio buffer is empty
    }
    pos = 0; // reset position if it exceeds buffer size
  }
  while(nwrite < len) {
    outl(AUDIO_SBUF_ADDR+pos+nwrite, (uint32_t)(ctl->buf.start + nwrite));
    nwrite += 3; // each write is 4 bytes (uint32_t)
  }
  outl(AUDIO_COUNT_ADDR, len);
  pos+= nwrite;
}
