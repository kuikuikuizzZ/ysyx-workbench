#ifndef __CPU_H__
#define __CPU_H__

#include <isa.h>
#include <common.h>
#include <verilated.h>
#include "Vysyx_24100012_top.h"

#define gpr(i) (top->ysyx_24100012_top__DOT__regfiles__DOT__reg_output_list[i])
#define get_csr(i) (top->ysyx_24100012_top__DOT__csrfiles__DOT__reg_output_list[i])
#define set_csr(i) (top->ysyx_24100012_top__DOT__csrfiles__DOT__reg_input_list[i])
#define top_dnpc (top->ysyx_24100012_top__DOT__ifu__DOT__PCIn)
#define top_pc (top->ysyx_24100012_top__DOT__pc)
#define top_halt (top->io_halt)
#define top_inst (top->ysyx_24100012_top__DOT__inst)

#define NPCTRAP(thispc, code) set_npc_state(NPC_END, thispc, code)

extern Vysyx_24100012_top* top ;

void init_cpu(int argc ,char** argv);
int cpu_exec(uint64_t n);
void free_cpu();
void set_npc_state(int state, vaddr_t pc, int halt_ret);
void isa_reg_display();
void init_itrace();
#endif