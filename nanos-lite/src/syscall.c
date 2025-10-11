#include <common.h>
#include "syscall.h"

enum {
  SYS_exit,
  SYS_yield,
  SYS_open,
  SYS_read,
  SYS_write,
  SYS_kill,
  SYS_getpid,
  SYS_close,
  SYS_lseek,
  SYS_brk,
  SYS_fstat,
  SYS_time,
  SYS_signal,
  SYS_execve,
  SYS_fork,
  SYS_link,
  SYS_unlink,
  SYS_wait,
  SYS_times,
  SYS_gettimeofday
};
void halt(int code);

size_t sys_write(intptr_t fd, void* buf, size_t len){
  if (fd == 1 || fd == 2) for (size_t i = 0; i < len; i++) putch(((char *)buf)[i]);
  return len;
}

intptr_t sys_brk(int* addr, intptr_t increment){
  *addr += increment;
  return 0;
}

void strace(uintptr_t a[4]){
  printf("syscall type = %d, a1 = %d, a2 = %d, a3 = %d\n", a[0], a[1], a[2], a[3]);
}
void do_syscall(Context *c) {
  uintptr_t a[4];
  a[0] = c->GPR1;
  a[1] = c->GPR2;
  a[2] = c->GPR3;
  a[3] = c->GPR4;
  strace(a);
  switch (a[0]) {
    case SYS_exit: halt(a[1]); break;
    case SYS_yield: yield(); break;
    case SYS_write: c->GPRx = sys_write(a[1],(void*)a[2],a[3]); break;
    case SYS_brk: c->GPRx = sys_brk((int*)a[1],a[2]); break;
    default: panic("Unhandled syscall ID = %d", a[0]);
  }
}
