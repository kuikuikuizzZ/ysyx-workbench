#ifndef __NPC_H__
#define __NPC_H__
#include <common.h>

enum { NPC_RUNNING, NPC_STOP, NPC_END, NPC_ABORT, NPC_QUIT };

typedef struct {
  int state;
  vaddr_t halt_pc;
  uint32_t halt_ret;
} NPCState;

extern NPCState npc_state;
#endif