#ifndef __ISA_H__
#define __ISA_H__

#include <memory/memory.h>
#include <string.h>
#include <common.h>

// const int gpr_size = MUXDEF(CONFIG_RVE, 16, 32);
const int gpr_size = 16;
const int csr_size = 5;
typedef enum  {
  // STAP    = 0x180, 
  // MSTATUS = 0x300,
  // MTVEC   = 0x305,
  // MEPC    = 0x341,
  // MCAUSE  = 0x342,
  STAP    = 0, 
  MSTATUS = 1,
  MTVEC   = 2,
  MEPC    = 3,
  MCAUSE  = 4,
} csrs;

typedef struct {
    word_t gpr[gpr_size];
    vaddr_t pc; 
    word_t csr[csr_size];
} CPU_state;

// this is not consistent with uint8_t
// but it is ok since we do not access the array directly
static const uint32_t img [] = {
        0x00100513,      //addi a0 x0 1
        0x00150513,      //addi a0 a0 1
        0x00150513,      //addi a0 a0 1
        0x00150513,      //addi a0 a0 1
        0x00150513,      //addi a0 a0 1
        0x00000513,      //addi a0 x0 0
        0x00100073,      //ebreak
};
bool isa_difftest_checkregs(CPU_state *ref_r, vaddr_t pc);

static void init_isa(){
/* Load built-in image. */
    memcpy(guest_to_host(MBASE), img, sizeof(img));
}

#endif