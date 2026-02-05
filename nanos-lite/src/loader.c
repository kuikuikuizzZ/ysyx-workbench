#include <proc.h>
#include <elf.h>
#include <ramdisk.h>
#include <fs.h> 
#include <loader.h>
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

static uintptr_t loader(PCB *pcb, const char *filename) {
  int fd = fs_open(filename, 0, 0);
  if (fd < 0) {
    Log("cannot open file %s\n", filename);
    naive_uload(NULL,"/bin/nterm");  
  }
  Elf_Ehdr ehdr;
  if (fs_read(fd,&ehdr,sizeof(ehdr)) != sizeof(ehdr)) {
    panic("read() %s failed",filename);
  }
  if (elf_check_file(&ehdr)) {
    panic("Invalid ELF file: %s", filename);
  }
  Elf_Phdr *phdr=malloc(ehdr.e_phnum*ehdr.e_phentsize);

  fs_lseek(fd, ehdr.e_phoff, SEEK_SET);
  if (fs_read(fd,phdr,ehdr.e_phentsize*ehdr.e_phnum) != ehdr.e_phentsize*ehdr.e_phnum) {
    panic("read() %s phdr failed",filename);
  }
  for (int i = 0; i < ehdr.e_phnum; i++) { 
    if (phdr[i].p_type == PT_LOAD) {
      memset((void *)(phdr[i].p_vaddr + phdr[i].p_filesz), 0, phdr[i].p_memsz - phdr[i].p_filesz);
      fs_lseek(fd, phdr[i].p_offset, SEEK_SET);
      if (fs_read(fd,(void *)phdr[i].p_vaddr, phdr[i].p_filesz) != phdr[i].p_filesz) {
        panic("read() %s load phdr failed",filename);
      }
    }
  }
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
    
  uintptr_t stk = ((uintptr_t)heap.end +sizeof(uintptr_t))-4;

  stk = (uintptr_t)ALIGN(stk, 4);
  stk -= envp_len + argv_len + 4; // 4 bytes for argc
  uintptr_t stk_start = stk;
  uintptr_t envp_start = stk + 4 + argv_len;
  uintptr_t argv_start = stk + 4;
  *(uint32_t*)stk = argc;
  if (argv != NULL) memcpy((void*)(stk + 4), argv, argv_len);
  if (envp != NULL) memcpy((void*)(stk + 4 + argv_len), envp, envp_len);

  uintptr_t entry = loader(p, filename);
  p->cp = ucontext(NULL, (Area) { p->stack, p->stack+STACK_SIZE   }, (void*)entry);
  p->cp->GPRx = (uintptr_t)stk_start;
  p->cp->GPR3 = (uintptr_t)argv_start;
  p->cp->GPR4 = (uintptr_t)envp_start;
  // should not set sp directly, should not suppose ISA is riscv
  // p->cp->GPRSP = (uintptr_t)stk;
  return p->cp;
}
Context* context_kload(PCB *p, void (*entry)(void *), void *arg) {
  p->cp = kcontext((Area) {  p->stack, p+1 }, entry, arg);
  return p->cp;
}
