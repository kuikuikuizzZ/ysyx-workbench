#ifndef __CPU_H__
#define __CPU_H__

#include <isa.h>
#include <common.h>
#include <verilated.h>
#include "Vysyx_24100012_top.h"

void init_cpu(int argc ,char** argv);
int cpu_exec(uint64_t n);
void free_cpu();
void set_npc_state(int state, vaddr_t pc, int halt_ret);
void isa_reg_display();
#define NPCTRAP(thispc, code) set_npc_state(NPC_END, thispc, code)

#endif