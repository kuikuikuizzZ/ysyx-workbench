#include <am.h>
#include <nemu.h>
#include <klib.h>


static AddrSpace kas = {};
static void* (*pgalloc_usr)(int) = NULL;
static void (*pgfree_usr)(void*) = NULL;
static int vme_enable = 0;

static Area segments[] = {      // Kernel memory mappings
  NEMU_PADDR_SPACE
};

#define USER_SPACE RANGE(0x40000000, 0x80000000)

static inline void set_satp(void *pdir) {
  uintptr_t mode = 1ul << (__riscv_xlen - 1);
  asm volatile("csrw satp, %0" : : "r"(mode | ((uintptr_t)pdir >> 12)));
}

static inline uintptr_t get_satp() {
  uintptr_t satp;
  asm volatile("csrr %0, satp" : "=r"(satp));
  return satp << 12;
}

bool vme_init(void* (*pgalloc_f)(int), void (*pgfree_f)(void*)) {
  pgalloc_usr = pgalloc_f;
  pgfree_usr = pgfree_f;

  kas.ptr = pgalloc_f(PGSIZE);
  int i;
  for (i = 0; i < LENGTH(segments); i ++) {
    void *va = segments[i].start;
    for (; va < segments[i].end; va += PGSIZE) {
      map(&kas, va, va, 0);
    }
  }
  set_satp(kas.ptr);
  vme_enable = 1;
  printf("vme init done");
  return true;
}

void protect(AddrSpace *as) {
  PTE *updir = (PTE*)(pgalloc_usr(PGSIZE));
  as->ptr = updir;
  as->area = USER_SPACE;
  as->pgsize = PGSIZE;
  // map kernel space
  memcpy(updir, kas.ptr, PGSIZE);
}

void unprotect(AddrSpace *as) {
}

void __am_get_cur_as(Context *c) {
  c->pdir = (vme_enable ? (void *)get_satp() : NULL);
}

void __am_switch(Context *c) {
  if (vme_enable && c->pdir != NULL) {
    set_satp(c->pdir);
  }
}

void map(AddrSpace *as, void *va, void *pa, int prot) {
  // printf("map: va: %p, pa: %p\n", va, pa);
  uintptr_t pt1 = (uintptr_t )as->ptr;
  assert((pt1 & 0xfff) == 0); // page aligned
  uintptr_t vpn1 = ((uintptr_t)va >> 22) & 0x3ff;
  uintptr_t vpn0 = ((uintptr_t)va >> 12) & 0x3ff;

  
  uintptr_t* pte1 = (uintptr_t*)(pt1 | (vpn1 << 2));
  uintptr_t pt0;
  // pte1 is valid entry?
  if ((*pte1 & 0x1) == 0) {
     pt0 = (uintptr_t)pgalloc_usr(PGSIZE);
     *pte1 = (pt0 >> 2)  | 0x1;
    //  printf("[riscv] map: pt1 %p, pte1_addr: %p, *pte1: %x\n",pt0, pte1, *pte1);
  } else {
    // pte1 is the physical address of the page table
    pt0 = (*pte1 << 2) & ~0xfff;
  }
  assert((pt0 & 0xfff) == 0);

  
  uintptr_t* pte0 = (uintptr_t*)(pt0 | (vpn0 << 2));
  // pte0 is the physical address of the page, xwr = 111, valid = 1
  *pte0 = ((uintptr_t)pa>>2 & ~0x3ff) | 0xf;
}

Context *ucontext(AddrSpace *as, Area kstack, void *entry) {
  Context *c  = kstack.end-sizeof(Context);              // ? pointer kstart 
  c->pdir     = as->ptr;
  c->mcause   = 11;
  c->mstatus  = 0x1800;   
  c->mepc     = (uint32_t)entry;             // mepc is set to entry 
  return c;
}
