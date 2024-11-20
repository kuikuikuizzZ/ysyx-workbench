#ifndef __NPC_H__
#define __NPC_H__

enum { NPC_RUNNING, NPC_STOP, NPC_END, NPC_ABORT, NPC_QUIT };

typedef struct {
  int uinitstate;
  vaddr_t halt_pc;
  uint32_t halt_ret;
} NPCState;


#endif