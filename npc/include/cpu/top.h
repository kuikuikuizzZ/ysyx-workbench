#include "verilated_fst_c.h"
#include <generated/autoconf.h>

#ifdef CONFIG_NPC_VERILOG
#include "VysyxSoCFull_top.h"
#include "VysyxSoCFull_top___024root.h"

typedef VysyxSoCFull_top Top;
typedef VysyxSoCFull_top___024root Top_rootp;
#endif

#ifdef CONFIG_NPC_CHISEL
#include "VysyxSoCFull.h"
#include "VysyxSoCFull___024root.h"
typedef VysyxSoCFull Top;
typedef VysyxSoCFull___024root Top_rootp;
uint32_t top_state();
#endif
#define NPCTRAP(thispc, code) set_npc_state(NPC_END, thispc, code)

typedef VerilatedFstC Tfp;

Top* top();
Tfp* tfp();


uint32_t top_gpr(int i);

uint32_t top_csr(int i);

uint32_t top_pc();

uint32_t top_halt();

uint32_t top_inst();

uint32_t top_dnpc();

void delete_top();

void watch_top();