#include <common.h>

#if defined(MULTIPROGRAM) && !defined(TIME_SHARING)
# define MULTIPROGRAM_YIELD() yield()
#else
# define MULTIPROGRAM_YIELD()
#endif

#define NAME(key) \
  [AM_KEY_##key] = #key,

static const char *keyname[256] __attribute__((used)) = {
  [AM_KEY_NONE] = "NONE",
  AM_KEYS(NAME)
};

size_t serial_write(const void *buf, size_t offset, size_t len) {
  for (size_t i = 0; i < len; i++) putch(((char *)buf)[i]);
  return len;
}

size_t events_read(void *buf, size_t offset, size_t len) {
  AM_INPUT_KEYBRD_T ev=io_read(AM_INPUT_KEYBRD);
  size_t key_size =0;
  if (ev.keycode == AM_KEY_NONE)  {
    return 0; }
  while(ev.keycode != AM_KEY_RETURN && ev.keycode != AM_KEY_NONE){
   
    if (ev.keydown){
      memcpy(buf+key_size, keyname[ev.keycode], sizeof(keyname[ev.keycode]));
      key_size += sizeof(keyname[ev.keycode]); 
      //  ((char *)buf)[key_size]=ev.keycode
      //  key_size++;
    }   
    ev=io_read(AM_INPUT_KEYBRD);
  } 
  ((char *)buf)[key_size] = '\0';
  return key_size;
}

size_t dispinfo_read(void *buf, size_t offset, size_t len) {
  int width = io_read(AM_GPU_CONFIG).width;
  int height = io_read(AM_GPU_CONFIG).height;
  int width_height[2] = {width, height};
  memcpy(buf, width_height, sizeof(width_height));

  return 0;
}

size_t fb_write(const void *buf, size_t offset, size_t len) {
  io_write(AM_GPU_MEMCPY, offset, (void *)buf, len);
  return len;
}

void init_device() {
  Log("Initializing devices...");
  ioe_init();
}
