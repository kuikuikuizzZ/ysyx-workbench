#include <common.h>
#include <memory.h>
#include <cpu.h>
#include <isa.h>
#include "monitor/sdb.h"

void load_prog(const char *img_file) {
    if (!img_file){
        printf("Use default img\n");
        return;
    }
    printf("use image: %s \n",img_file);
    FILE *fp = fopen(img_file, "rb");
    fseek(fp, 0, SEEK_END);
    long size = ftell(fp);
    fseek(fp, 0, SEEK_SET);
    int ret = fread(guest_to_host(MBASE), 1, size, fp);
    assert(ret == size);

    fclose(fp);
}


int main(int argc, char** argv) {
    init_memory();
    init_isa();
    init_cpu(argc,argv);

    load_prog(argv[1]);
    
    int ret = cpu_exec();

    // Return good completion status
    return ret;
}
