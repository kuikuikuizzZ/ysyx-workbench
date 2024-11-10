#include <am.h>
#include <nemu.h>
#include <klib.h>

#define SYNC_ADDR (VGACTL_ADDR + 4)
static int w = 0,h=0;
static uint32_t *fb = NULL;
void __am_gpu_init() {
  w = inw(VGACTL_ADDR+2);  // TODO: get the correct width
  h = inw(VGACTL_ADDR);  // TODO: get the correct height
  fb = (uint32_t *)(uintptr_t)FB_ADDR;
  for (int i = 0; i < w * h; i ++) fb[i] = i;
  outl(SYNC_ADDR, 1);
}

void __am_gpu_config(AM_GPU_CONFIG_T *cfg) {
  *cfg = (AM_GPU_CONFIG_T) {
    .present = true, .has_accel = false,
    .width = inw(VGACTL_ADDR+2), .height = inw(VGACTL_ADDR),
    .vmemsz = inl(FB_ADDR),
  };
}

void __am_gpu_fbdraw(AM_GPU_FBDRAW_T *ctl) {
  memcpy(fb+ctl->x+(ctl->y)*w,ctl->pixels,ctl->w*ctl->h);
  if (ctl->sync) {
    outl(SYNC_ADDR, 1);
  }
}

void __am_gpu_status(AM_GPU_STATUS_T *status) {
  status->ready = true;
}
