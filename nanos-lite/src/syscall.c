#include <common.h>
#include "syscall.h"
#include <fs.h>
#include <proc.h>
#include <loader.h>

void halt(int code);
struct timeval
{
  uint32_t tv_sec;
  uint32_t tv_usec;
};
int sys_gettimeofday(struct timeval* tv, void* tz){
  tv->tv_usec = io_read(AM_TIMER_UPTIME).us%1000000;
  tv->tv_sec = io_read(AM_TIMER_UPTIME).us/1000000;
  return 0;
}
intptr_t sys_brk(int* addr, intptr_t increment){
  return 0;
}

int sys_execve(const char *fname, char * const argv[], char *const envp[]){
  PCB* pcb = next_available_pcb();
  if (pcb == NULL) {
    Log("No available PCB for execve %s", fname);
    return -1;
  }
  context_uload(pcb,fname,argv,envp);  
  // ????? should reserve ?
  // switch_boot_pcb();
  yield();
  return 0;
}

void sys_exit(int code){
  yield();
  sys_execve("/bin/nterm", NULL, NULL);
}



void strace(uintptr_t a[4]){
  if (a[0] == SYS_write || a[0] == SYS_read || 
    a[0] == SYS_lseek || a[0] == SYS_close || 
    a[0] == SYS_open)
  {   char* name = get_filename(a[1])? get_filename(a[1]) : "NULL";
      printf("syscall type = %d, a1 = %s, a2 = %x, a3 = %x\n", a[0], name, a[2], a[3]);
  }
  else printf("syscall type = %d, a1 = %x, a2 = %x, a3 = %x\n", a[0], a[1], a[2], a[3]);
}
void do_syscall(Context *c) {
  uintptr_t a[4];
  a[0] = c->GPR1;
  a[1] = c->GPR2;
  a[2] = c->GPR3;
  a[3] = c->GPR4;
  #ifdef STRACE
  strace(a);
  #endif
  switch (a[0]) {
    // case SYS_exit: sys_exit(a[1]); break;
    case SYS_exit: yield(); c->GPRx=0;  break;
    case SYS_yield: yield(); c->GPRx=0; break;
    case SYS_brk: c->GPRx = sys_brk((int*)a[1],a[2]); break;
    case SYS_write: c->GPRx = sys_write(a[1],(void*)a[2],a[3]); break;
    case SYS_open: c->GPRx = fs_open((const char*)a[1],a[2],a[3]); break;
    case SYS_read: c->GPRx = sys_read(a[1],(void*)a[2],a[3]); break;
    case SYS_lseek: c->GPRx = fs_lseek(a[1],a[2],a[3]); break;
    case SYS_close: c->GPRx = fs_close(a[1]); break;
    case SYS_execve: c->GPRx = sys_execve((const char*)a[1], (char* const*)a[2], (char* const*)a[3]); break;
    case SYS_gettimeofday: c->GPRx = sys_gettimeofday((struct timeval*)a[1],(void*)a[2]); break;
    default: panic("Unhandled syscall ID = %d", a[0]);
  }
}
