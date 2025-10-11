#include <proc.h>
#include <elf.h>
#include <ramdisk.h>

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
  Elf_Ehdr ehdr;
  if (ramdisk_read(&ehdr,0,sizeof(ehdr)) != sizeof(ehdr)) {
    panic("ramdisk_read() failed");
  }
  if (elf_check_file(&ehdr)) {
    panic("Invalid ELF file: %s", filename);
  }
  Elf_Phdr *phdr=malloc(ehdr.e_phnum*ehdr.e_phentsize);

  if (ramdisk_read(phdr,ehdr.e_phoff,ehdr.e_phentsize*ehdr.e_phnum) != ehdr.e_phentsize*ehdr.e_phnum) {
    panic("ramdisk_read() phdr failed");
  }
  for (int i = 0; i < ehdr.e_phnum; i++) { 
    if (phdr[i].p_type == PT_LOAD) {
      memset((void *)phdr[i].p_vaddr, 0, phdr[i].p_memsz);
      ramdisk_read((void *)phdr[i].p_vaddr, phdr[i].p_offset, phdr[i].p_filesz);
    }
  }
  return ehdr.e_entry;
}

void naive_uload(PCB *pcb, const char *filename) {
  uintptr_t entry = loader(pcb, filename);
  Log("Jump to entry = %p", entry);
  ((void(*)())entry) ();
}

