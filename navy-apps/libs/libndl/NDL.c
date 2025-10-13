#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <assert.h>

static int evtdev = -1;
static int fbdev = -1;
static int screen_w = 0, screen_h = 0;
static int offset_x = 0, offset_y = 0;
uint32_t NDL_GetTicks() {
  struct timeval tv ;
  assert(gettimeofday(&tv, NULL) == 0);

  return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

int NDL_PollEvent(char *buf, int len) {
  int ret = read(evtdev, buf, len);
  return ret;
}

void NDL_OpenCanvas(int *w, int *h) {
  if (getenv("NWM_APP")) {
    int fbctl = 4;
    fbdev = 5;
    screen_w = *w; screen_h = *h;
    char buf[64];
    int len = sprintf(buf, "%d %d", screen_w, screen_h);
    // let NWM resize the window and create the frame buffer
    write(fbctl, buf, len);
    while (1) {
      // 3 = evtdev
      int nread = read(3, buf, sizeof(buf) - 1);
      if (nread <= 0) continue;
      buf[nread] = '\0';
      if (strcmp(buf, "mmap ok") == 0) break;
    }
    close(fbctl);
  }else {
    int dispinfo_fd = open("/proc/dispinfo", 0, 0);
    
    int width_height[2] ; 
    if (read(dispinfo_fd,width_height, sizeof(width_height)) != sizeof(width_height)) printf("read width and height failed\n");
    screen_w = width_height[0];
    screen_h = width_height[1];
    close(dispinfo_fd);
    printf("screen_w = %d, screen_h = %d\n", screen_w, screen_h);

    assert(*w <= screen_w && *h <= screen_h);
    if (*w || *h) {
      offset_x = (screen_w - *w) / 2;
      offset_y = (screen_h - *h) / 2;
    } else {
      *w = screen_w;
      *h = screen_h;
    }

    fbdev = open("/dev/fb", 0,0 );
  }
}

void NDL_DrawRect(uint32_t *pixels, int x, int y, int w, int h) {
  x += offset_x; y += offset_y;
  for (int i = 0; i < h; ++i) {
    lseek(fbdev, ((y + i) * screen_w + x) * 4, SEEK_SET);
    assert(write(fbdev, pixels + w * i, w * 4) == w * 4);
  }
}

void NDL_OpenAudio(int freq, int channels, int samples) {
}

void NDL_CloseAudio() {
}

int NDL_PlayAudio(void *buf, int len) {
  return 0;
}

int NDL_QueryAudio() {
  return 0;
}

int NDL_Init(uint32_t flags) {
  if (getenv("NWM_APP")) {
    evtdev = 3;
  } else {
    evtdev = open("/dev/events", 0,0);
  }
  
  return 0;
}

void NDL_Quit() {
  if (evtdev != -1) close(evtdev);

}
