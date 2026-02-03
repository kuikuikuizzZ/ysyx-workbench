#include <proc.h>
#include <elf.h>
#include <ramdisk.h>
#include <fs.h> 
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

Context* context_uload (PCB *p, const char *filename) {
  uintptr_t entry = loader(p, filename);
  p->cp = ucontext(NULL, (Area) {  p->stack, p+1 }, (void*)entry);
  return p->cp;
}