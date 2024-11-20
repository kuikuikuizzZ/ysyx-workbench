#ifndef __CPU_H__
#define __CPU_H__

#include <verilated.h>
#include "Vysyx_24100012_top.h"
#include <isa.h>
#include <common.h>

void init_cpu(int argc ,char** argv);

int cpu_exec(uint64_t n);

void free_cpu();

#endif