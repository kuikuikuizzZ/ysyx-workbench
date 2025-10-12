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

enum {FD_STDIN, FD_STDOUT, FD_STDERR, FD_FB};

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
  [FD_STDIN]  = {"stdin", 0, 0, 0,  invalid_read, invalid_write},
  [FD_STDOUT] = {"stdout", 0, 0, 0, invalid_read, invalid_write},
  [FD_STDERR] = {"stderr", 0, 0, 0, invalid_read, invalid_write},
#include "files.h"
};

size_t fs_read(int fd,void *buf, size_t len) {
  if (fd < 0 || fd >= sizeof(file_table) / sizeof(file_table[0]) || 
      (file_table[fd].open_offset+len) > file_table[fd].size ) {
    return -1;
  }
  size_t ret = ramdisk_read(buf, file_table[fd].disk_offset + file_table[fd].open_offset, len);
    if( ret == len){
    file_table[fd].open_offset += len;
  }
  return ret;
}

size_t fs_write(int fd, const void *buf,  size_t len) {
 if (fd < 0 || fd >= sizeof(file_table) / sizeof(file_table[0]) || 
    (file_table[fd].open_offset+len) > file_table[fd].size ) {
    return -1;
  }
  size_t ret = ramdisk_write(buf, file_table[fd].disk_offset + file_table[fd].open_offset, len);
  if( ret == len){
    file_table[fd].open_offset += len;
  }
  return ret;
}

size_t fs_lseek(int fd, size_t offset, int whence){
  if (whence == SEEK_SET) {
    // do nothing
  } else if (whence == SEEK_CUR) {
    offset += file_table[fd].open_offset;
  } else if (whence == SEEK_END) {
    offset += file_table[fd].size;
  } else {
    assert(0);
  }
  if (offset > file_table[fd].size) return -1;
  file_table[fd].open_offset = offset; 
  return offset;
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

void init_fs() {
  // TODO: initialize the size of /dev/fb
}
