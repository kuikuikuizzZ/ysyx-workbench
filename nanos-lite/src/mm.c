#include <memory.h>
#include <proc.h>
static void *pf = NULL;

void* new_page(size_t nr_page) {
  
  return pf += nr_page * PGSIZE;
}

#ifdef HAS_VME
static void* pg_alloc(int n) {
  size_t nr_page = ROUNDUP(n, PGSIZE) / PGSIZE;
  void *p = new_page(nr_page);
  memset(p, 0, nr_page * PGSIZE);
  return p;
}
#endif

void free_page(void *p) {
  panic("not implement yet");
}

/* The brk() system call handler. */
int mm_brk(uintptr_t brk) {
  printf("mm_brk: %x, curr %x\n", brk,current->max_brk);
  if (current->max_brk >= brk) return 0;
  while(current->max_brk < brk){
    map(&(current->as), (void*)current->max_brk,new_page(1),0);
    current->max_brk += PGSIZE;
  }
  return 0;
}

void init_mm() {
  pf = (void *)ROUNDUP(heap.start, PGSIZE);
  Log("free physical pages starting from %p", pf);
#ifdef HAS_VME
  vme_init(pg_alloc, free_page);
#endif
}
