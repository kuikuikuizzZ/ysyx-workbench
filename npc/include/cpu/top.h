#ifdef CONFIG_NPC_VERILOG
#include "Vysyx_24100012_top.h"
#include "Vysyx_24100012_top___024root.h"

typedef Vysyx_24100012_top Top;
typedef Vysyx_24100012_top___024root Top_rootp;
#endif
#ifdef CONFIG_NPC_CHISEL
#include "VTop.h"
#include "VTop___024root.h"
typedef VTop Top;
typedef VTop___024root Top_rootp;
#endif

#define NPCTRAP(thispc, code) set_npc_state(NPC_END, thispc, code)


Top* top() ;

uint32_t top_gpr(int i);

uint32_t top_csr(int i);

uint32_t top_pc();

uint32_t top_halt();

uint32_t top_inst();

uint32_t top_dnpc();

void delete_top();

void watch_top();