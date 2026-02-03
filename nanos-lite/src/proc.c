#include <proc.h>
#include <loader.h>

#define MAX_NR_PROC 4

static PCB pcb[MAX_NR_PROC] __attribute__((used)) = {};
static PCB pcb_boot = {};
PCB *current = NULL;

void switch_boot_pcb() {
  current = &pcb_boot;
}

void hello_fun(void *arg) {
  int j = 1;
  while (1) {
    Log("Hello World from Nanos-lite with arg '%s' for the %dth time!", (char *)arg, j);
    j ++;
    yield();
  }
}



PCB* next_current() {
  PCB *start = current;
  do {
      // 指针算术：移动到下一个PCB，循环数组
      current = (current - pcb + 1) % MAX_NR_PROC + pcb;
      if (current == start) {
          // 未找到有效PCB,继续使用当前PCB
          break;
      }
  } while (current->cp == NULL);
  return current;
}

PCB* next_available_pcb() {
  for (int i = 0; i < MAX_NR_PROC; i++) {
      if (pcb[i].cp == NULL) {
          return &pcb[i];
      }
  }
  return NULL; // No available PCB found
}
Context* schedule(Context *prev) {
  current->cp = prev;
  current = next_current();
  return current->cp; 
}

Context* context_kload(PCB *p, void (*entry)(void *), void *arg) {
  p->cp = kcontext((Area) {  p->stack, p+1 }, entry, arg);
  return p->cp;
}


void init_proc() {
  // context_kload(&pcb[0], hello_fun, (void *)"A");
  // context_uload(&pcb[1], "/bin/bmp-test");
  switch_boot_pcb();
  Log("Initializing processes...");

  // load program here
  naive_uload(&pcb[0], "/bin/event-test");
}

