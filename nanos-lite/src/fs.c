#include <fs.h>
#include <ramdisk.h>

typedef size_t (*ReadFn) (void *buf, size_t offset, size_t len);
typedef size_t (*WriteFn) (const void *buf, size_t offset, size_t len);

typedef struct {
  char *name;
  size_t size;
  size_t disk_offset;
  size_t open_offset;
  ReadFn read;
  WriteFn write;
} Finfo;

enum {FD_STDIN, FD_STDOUT, FD_STDERR, FD_FB,FD_EVENT,FD_DISINFO};


size_t serial_write(const void *buf, size_t offset, size_t len);
size_t events_read(void *buf, size_t offset, size_t len);
size_t dispinfo_read(void *buf, size_t offset, size_t len);
size_t fb_write(const void *buf, size_t offset, size_t len);

size_t invalid_read(void *buf, size_t offset, size_t len) {
  panic("should not reach here");
  return 0;
}

size_t invalid_write(const void *buf, size_t offset, size_t len) {
  panic("should not reach here");
  return 0;
}





/* This is the information about all files in disk. */
static Finfo file_table[] __attribute__((used)) = {
  [FD_STDIN]    = {"stdin", 0, 0, 0,  invalid_read, invalid_write},
  [FD_STDOUT]   = {"stdout", 0, 0, 0, invalid_read, serial_write},
  [FD_STDERR]   = {"stderr", 0, 0, 0, invalid_read, serial_write},
  [FD_FB]       = {"/dev/fb", 0, 0, 0, invalid_read, fb_write},
  [FD_EVENT]    = {"/dev/events", 0, 0, 0, events_read, invalid_write},
  [FD_DISINFO]  = {"/proc/dispinfo", 0, 0, 0, dispinfo_read, invalid_write},
#include "files.h"
};

size_t fs_read(int fd,void *buf, size_t len) {
  if (file_table[fd].open_offset == file_table[fd].size ||
     fd < 0 || fd >= sizeof(file_table) / sizeof(file_table[0])){
    return 0;
  }  
  if ( (file_table[fd].open_offset+len) >= file_table[fd].size ) {
    len = file_table[fd].size-file_table[fd].open_offset;
  }
  size_t ret = ramdisk_read(buf, file_table[fd].disk_offset + file_table[fd].open_offset, len);
  file_table[fd].open_offset += ret;
  return ret;
}
size_t sys_read(int fd, void* buf, size_t len){
  return file_table[fd].read != NULL? file_table[fd].read(buf,0, len) : fs_read(fd, buf, len);
}
size_t fs_write(int fd, const void *buf,  size_t len) {
  if (fd < 0 || fd >= sizeof(file_table) / sizeof(file_table[0]) || 
      file_table[fd].open_offset == file_table[fd].size){
    return 0;
  }
  if ((file_table[fd].open_offset+len) > file_table[fd].size ) {
    len = file_table[fd].size-file_table[fd].open_offset;
  }
  size_t ret = ramdisk_write(buf, file_table[fd].disk_offset + file_table[fd].open_offset, len);
  file_table[fd].open_offset += ret;
  return ret;
}
size_t sys_write(int fd, void* buf, size_t len){
  return file_table[fd].write != NULL? file_table[fd].write(buf,file_table[fd].open_offset, len) : fs_write(fd, buf, len);
}
size_t fs_lseek(int fd, size_t offset, int whence){
  assert(0 <= fd && fd < LENGTH(file_table));
  if (whence == SEEK_SET) {
    // do nothing
  } else if (whence == SEEK_CUR) {
    offset += file_table[fd].open_offset;
  } else if (whence == SEEK_END) {
    offset += file_table[fd].size;
  } else {
    assert(0);
  }
  if (offset > file_table[fd].size) {
    file_table[fd].open_offset = file_table[fd].size;
  } else{
    file_table[fd].open_offset = offset; 
  }
  return file_table[fd].open_offset;
}

int fs_open(const char *pathname, int flags, int mode) {
  for (int i = 0; i < sizeof(file_table) / sizeof(file_table[0]); i++) {
    if (strcmp(pathname,file_table[i].name) == 0) {
      file_table[i].open_offset = 0;
      return i;
    }
  }
  assert("file not found");
  return -1;
}

int fs_close(int fd) {
  return 0;
}

char* get_filename(int fd) {
  if (fd < 0 || fd >= sizeof(file_table) / sizeof(file_table[0])) {
    return NULL;
  }
  return file_table[fd].name;
}

void init_fs() {
  // TODO: initialize the size of /dev/fb
  AM_GPU_CONFIG_T cfg;
  ioe_read(AM_GPU_CONFIG, &cfg);
  file_table[FD_FB].size = cfg.vmemsz;
}
