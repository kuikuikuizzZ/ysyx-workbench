#include <stdint.h>
#include <stdlib.h>
#include <assert.h>

int main(int argc, char *argv[], char *envp[]);
extern char **environ;
char **argv;  
int argc=0;
void call_main(uintptr_t *args) {
  char *empty[] =  {NULL };
  uintptr_t sp;
  uintptr_t x10,x11, x12;
  asm volatile ("mv %0, a0" : "=r"(x10));
  asm volatile ("mv %0, a1" : "=r"(x11));
  asm volatile ("mv %0, a2" : "=r"(x12));

  argc = *(uint32_t*)x10;
  argv = (char **)(x11);
  environ = (char **)(x12);
  exit(main(argc, argv, environ));
  assert(0);
}
