#include <proc.h>
#include <elf.h>
#include <ramdisk.h>
#include <fs.h> 
#include <loader.h>
#include <memory.h>

#ifdef __LP64__
# define Elf_Ehdr Elf64_Ehdr
# define Elf_Phdr Elf64_Phdr
#else
# define Elf_Ehdr Elf32_Ehdr
# define Elf_Phdr Elf32_Phdr
#endif

#define ELFMAG "\177ELF"
int elf_check_file(Elf_Ehdr *header){
    return memcmp(header->e_ident,ELFMAG, 4);
}

void* mem_translate(PCB *pcb, uintptr_t vaddr) {
  uintptr_t pt1_addr = (uintptr_t)pcb->as.ptr;; // TODO: add real translation here
  // page table walk
  uintptr_t vpn1 = (vaddr>>22) & 0x3ff ;
  uintptr_t vpn0 = (vaddr>>12) & 0x3ff;
  uintptr_t pte1_addr = pt1_addr | (vpn1<<2);
  uintptr_t pte1 = *(uintptr_t*)pte1_addr;   // pte should be 4 bytes
  if ((pte1 & 0x1) == 0) return NULL;

  uintptr_t pt0_addr = (pte1) & ~0xfff;
  uintptr_t pte0_addr = pt0_addr | (vpn0 <<2);
  uintptr_t  pte0 = *(uintptr_t*)pte0_addr;   // pte should be 4 bytes

  void*  paddr = (void*)(((pte0) & ~0xfff) | (vaddr & 0xfff));
  if ((pte0 & 0x1)==0) return NULL;
  return paddr;
}

static uintptr_t loader(PCB *pcb, const char *filename) {
  int fd = fs_open(filename, 0, 0);
  if (fd < 0) {
    panic("cannot open file %s\n", filename);
  }
  Elf_Ehdr ehdr;
  if (fs_read(fd,&ehdr,sizeof(ehdr)) != sizeof(ehdr)) {
    panic("read() %s failed",filename);
  }
  if (elf_check_file(&ehdr)) {
    panic("Invalid ELF file: %s", filename);
  }
  Elf_Phdr *phdr=malloc(ehdr.e_phnum*ehdr.e_phentsize);
  pcb->max_brk = 0;
  fs_lseek(fd, ehdr.e_phoff, SEEK_SET);
  if (fs_read(fd,phdr,ehdr.e_phentsize*ehdr.e_phnum) != ehdr.e_phentsize*ehdr.e_phnum) {
    panic("read() %s phdr failed",filename);
  }
  for (int i = 0; i < ehdr.e_phnum; i++) { 
    if (phdr[i].p_type == PT_LOAD) {
      int offset = 0; 
      uintptr_t vaddr_end = phdr[i].p_vaddr + phdr[i].p_memsz;
      pcb->max_brk = MAX(pcb->max_brk, ROUNDUP(vaddr_end, PGSIZE));
      for (void *vaddr = (void*)ROUNDDOWN(phdr[i].p_vaddr,PGSIZE); vaddr < (void*)(vaddr_end); vaddr += PGSIZE){
        void* paddr = mem_translate(pcb, (uintptr_t)vaddr);
        if (!paddr) {
          paddr = new_page(1);
          map(&(pcb->as), vaddr, paddr,0);
        }
        /*          voffset sz      sz      sz
         |          |<---> <-->|<-------->|<-->      |         
                 vaddr    ^
                      last_end,p_vaddr
        */
        uintptr_t voffset = (uintptr_t)vaddr < phdr[i].p_vaddr?phdr[i].p_vaddr - (uintptr_t)vaddr:0 ;
        fs_lseek(fd, phdr[i].p_offset+offset, SEEK_SET);
        size_t sz = MIN(phdr[i].p_filesz - offset - voffset, PGSIZE - voffset); 
        if (fs_read(fd,paddr+voffset, sz) != sz) {
          panic("read() %s load phdr failed",filename);
        }
        // fill 0 for the rest
        if (sz != PGSIZE){
          memset(paddr + sz, 0, PGSIZE - sz);
        }
        offset += (PGSIZE-voffset);
      }
     
    }

  }
  printf("max_brk = %p\n",pcb->max_brk);
  fs_close(fd);
  free(phdr);
  return ehdr.e_entry;
}

void naive_uload(PCB *pcb, const char *filename) {
  uintptr_t entry = loader(pcb, filename);
  Log("Jump to entry = %p, file %s\n", entry, filename);
  ((void(*)())entry) ();
}

#define ALIGN(A,N) ((A)  & ~((N) - 1))

Context* context_uload (PCB *p, const char *filename, char *const argv[], char *const envp[]) {
  protect(&p->as);

  uint32_t argc = 0 ;
  uint32_t envc = 0 ;
  uint32_t argv_len = 0;
  uint32_t envp_len = 0;
  // stk -= STACK_SIZE;
  while (argv != NULL && argv[argc])
  {
    argv_len += (strlen(argv[argc]) + 1);
    argc++;
  }
  while (envp != NULL && envp[envc])
  {
    envp_len += (strlen(envp[envc]) + 1);
    envc++;
  }
    
  void* stk = new_page(8); // 8 pages for user stack
  // stack grows downward, return the start address
  stk += STACK_SIZE;
  stk -= envp_len + argv_len + 4; // 4 bytes for argc
  uintptr_t stk_start =  (uintptr_t)stk;
  uintptr_t envp_start = (uintptr_t)stk + 4 + argv_len;
  uintptr_t argv_start = (uintptr_t)stk + 4;
  *(uint32_t*)stk = argc;
  if (argv != NULL) memcpy((void*)(stk + 4), argv, argv_len);
  if (envp != NULL) memcpy((void*)(stk + 4 + argv_len), envp, envp_len);

  uintptr_t entry = loader(p, filename);
  AddrSpace *as = &p->as;
  p->cp = ucontext(as, (Area) { p->stack, p->stack+STACK_SIZE   }, (void*)entry);
  p->cp->GPRx = (uintptr_t)stk_start;
  p->cp->GPR3 = (uintptr_t)argv_start;
  p->cp->GPR4 = (uintptr_t)envp_start;
  // should not set sp directly, should not suppose ISA is riscv
  return p->cp;
}
Context* context_kload(PCB *p, void (*entry)(void *), void *arg) {
  p->cp = kcontext((Area) {  p->stack, p+1 }, entry, arg);
  return p->cp;
}
