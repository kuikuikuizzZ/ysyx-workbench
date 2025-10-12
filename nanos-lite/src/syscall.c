#include <common.h>
#include "syscall.h"
#include <fs.h>
void halt(int code);

size_t sys_write(intptr_t fd, void* buf, size_t len){
  if (fd == 1 || fd == 2) for (size_t i = 0; i < len; i++) putch(((char *)buf)[i]);
  else fs_write(fd, buf, len);
  return len;
}

// size_t sys_read(intptr_t fd, void* buf, size_t len){
//   if (fd == 0) for (size_t i = 0; i < len; i++) buf[i]=;
//   else fs_write(fd, buf, len);
//   return len;
// }

intptr_t sys_brk(int* addr, intptr_t increment){
  *addr += increment;
  return 0;
}

void strace(uintptr_t a[4]){
  printf("syscall type = %d, a1 = %x, a2 = %x, a3 = %x\n", a[0], a[1], a[2], a[3]);
}
void do_syscall(Context *c) {
  uintptr_t a[4];
  a[0] = c->GPR1;
  a[1] = c->GPR2;
  a[2] = c->GPR3;
  a[3] = c->GPR4;
  // strace(a);
  switch (a[0]) {
    case SYS_exit: halt(a[1]); break;
    case SYS_yield: yield(); break;
    case SYS_brk: c->GPRx = sys_brk((int*)a[1],a[2]); break;
    case SYS_write: c->GPRx = sys_write(a[1],(void*)a[2],a[3]); break;
    case SYS_open: c->GPRx = fs_open((const char*)a[1],a[2],a[3]); break;
    case SYS_read: c->GPRx = fs_read(a[1],(void*)a[2],a[3]); break;
    case SYS_lseek: c->GPRx = fs_lseek(a[1],a[2],a[3]); break;
    case SYS_close: c->GPRx = fs_close(a[1]); break;
    default: panic("Unhandled syscall ID = %d", a[0]);
  }
}
