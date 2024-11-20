#include <common.h>
#include <memory.h>
#include <cpu.h>
#include <isa.h>
#include "monitor/sdb.h"
#include <getopt.h>

void sdb_set_batch_mode();
char* img_file =NULL;

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


static int parse_args(int argc, char **argv) {
  const struct option table[] = {
    {"batch"    , no_argument      , NULL, 'b'},
    // {"log"      , required_argument, NULL, 'l'},
    // {"diff"     , required_argument, NULL, 'd'},
    // {"port"     , required_argument, NULL, 'p'},
    // {"help"     , no_argument      , NULL, 'h'},
    // {"elf"      , required_argument, NULL, 'e'},
    {0          , 0                , NULL,  0 },
  };
  int o;
  while ( (o = getopt_long(argc, argv, "-bhl:d:p:e:", table, NULL)) != -1) {
    switch (o) {
      case 'b': sdb_set_batch_mode(); break;
    //   case 'p': sscanf(optarg, "%d", &difftest_port); break;
    //   case 'l': log_file = optarg; break;
    //   case 'd': diff_so_file = optarg; break;
    //   case 'e': elf_file = optarg;break;
      case 1: img_file = optarg; return 0;
      default:
        printf("Usage: %s [OPTION...] IMAGE [args]\n\n", argv[0]);
        printf("\t-b,--batch              run with batch mode\n");
        // printf("\t-l,--log=FILE           output log to FILE\n");
        // printf("\t-d,--diff=REF_SO        run DiffTest with reference REF_SO\n");
        // printf("\t-p,--port=PORT          run DiffTest with port PORT\n");
        printf("\n");
        exit(0);
    }
  }
  return 0;
}

int main(int argc, char** argv) {
    parse_args(argc, argv);
    init_memory();
    init_isa();
    init_cpu(argc,argv);

    load_prog(img_file);
    
    sdb_mainloop();
    free_cpu();

    // Return good completion status
    return 0;
}
