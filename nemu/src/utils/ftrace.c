#include <ftrace.h>
#include <elf.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
// #include <stdlib.h>

int elf_check_file(Elf32_Ehdr *header){
    char temp[4];
    memcpy(temp,header->e_ident,4);
    int result = memcmp(temp, ELFMAG, 4);
    return result;
}

void init_ftrace(char* elf_file){
    FILE *fp = fopen(elf_file, "rb");
    if (fp == NULL) {
        fprintf(stderr, "Unable to open '%s': %s\n", elf_file, strerror(errno));
        return;
    }

    Elf32_Ehdr ehdr;
    if (fread(&ehdr, sizeof(ehdr), 1, fp) != 1) {
        fprintf(stderr, "fread: %s\n", strerror(errno));
        fclose(fp);
        return;
    }

    if (elf_check_file(&ehdr)) {
        fprintf(stderr, "'%s' is not an ELF file\n", elf_file);
        fclose(fp);
        return;
    }
    /* Parse the rest of the ELF file here. */

    /* Don't forget to close the file. */
    fclose(fp);
    return;
}

