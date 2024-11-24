#include <am.h>
#include <npc.h>
#include <klib.h>
#include <riscv.h>
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
  w = cfg->width;
  h = cfg->height;
}

void __am_gpu_fbdraw(AM_GPU_FBDRAW_T *ctl) {
  uint32_t* p = ctl->pixels;
  // for(int i=0;i<ctl->h;i++){
  //   memcpy(fb+ctl->x+(ctl->y+i)*w,p+ctl->w*i,ctl->w*sizeof(uint32_t));
  // }

  for(int i=0;i<ctl->h;i++){
    for(int j=0;j<ctl->w;j++){
      fb[(ctl->y+i)*w+ctl->x+j] = p[i*ctl->w+j];
    }
  }

  if (ctl->sync) {
    outl(SYNC_ADDR, 1);
  }
}

void __am_gpu_status(AM_GPU_STATUS_T *status) {
  status->ready = true;
}
