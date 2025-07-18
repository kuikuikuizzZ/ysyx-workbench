
#include <cpu/cpu.h>



#ifdef CONFIG_NPC_CHISEL
#include <cpu/top.h>
#ifndef __DEBUG_TOP__
#define __DEBUG_TOP__
static uint32_t pc = 0;
static uint32_t inst = 0;
static uint32_t halt = 0;


extern "C" void dpi_port(int in_halt, int in_pc, int in_inst){
    pc = in_pc;
    inst = in_inst;
    halt = in_halt;
}

#endif
Top* _top = NULL;
Top_rootp* _rootp =NULL;

Top* top() {
    if (!_top) {
        _top =  new Top{};
        _rootp = _top->rootp;
    }
    return _top;
}




uint32_t top_gpr(int i) {
    if (!_rootp) return 0;
    if (i < 0 || i >= gpr_size) {
        printf("gpr index %d out of range\n", i);
        return 0;
    }
    uint32_t gpr_i ;
    IFDEF(CONFIG_SOC,gpr_i=(uint32_t)_rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__reg_file__DOT__regfile_ext__DOT__Memory[i];);
    IFNDEF(CONFIG_SOC,gpr_i=(uint32_t)_rootp->ysyxSoCFull__DOT__core__DOT__reg_file__DOT__regfile_ext__DOT__Memory[i]);
    return gpr_i;
}

uint32_t top_csr(int i) {
    if (!_top) return 0;
    if (i < 0 || i >= csr_size) {
        printf("csr index %d out of range\n", i);
        return 0;
    }
    return 0;
}

uint32_t top_pc() {
    if (!_rootp) return 0;
    uint32_t pc ;
    IFDEF(CONFIG_SOC,pc=(uint32_t)_rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__inst_fetch__DOT__pc_reg);
    IFNDEF(CONFIG_SOC,pc=(uint32_t)_rootp->ysyxSoCFull__DOT__core__DOT__inst_fetch__DOT__pc_reg);
    return pc;
}

uint32_t top_halt(){
    if (!_rootp) return 0;
    return halt;
}


uint32_t top_inst() {
    if (!_rootp) return 0;
    return inst;
}
uint32_t top_dnpc() {
    if (!_rootp) return 0;
    uint32_t pc;
    IFDEF(CONFIG_SOC,pc=(uint32_t)_rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__inst_fetch__DOT__casez_tmp);
    IFNDEF(CONFIG_SOC,pc=(uint32_t)_rootp->ysyxSoCFull__DOT__core__DOT__inst_fetch__DOT__casez_tmp);
    return pc;
}

// uint32_t top_state(){
//     if (!_rootp) return 0;
//     return _rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__c__DOT__state;
// }

void delete_top() {
    if (_top) {
        delete _top ;
    }
}
uint32_t top_op1() {
    if (!_rootp) return 0;
    uint32_t op1;
    IFDEF(CONFIG_SOC,op1=(uint32_t)_rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__d__DOT__casez_tmp_0);
    IFNDEF(CONFIG_SOC,op1=(uint32_t)_rootp->ysyxSoCFull__DOT__core__DOT__d__DOT__casez_tmp_0);
    return op1;
}

uint32_t top_op2() {
    if (!_rootp) return 0;
    uint32_t op2;
    IFDEF(CONFIG_SOC,op2=(uint32_t)_rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__d__DOT__casez_tmp_1);
    IFNDEF(CONFIG_SOC,op2=(uint32_t)_rootp->ysyxSoCFull__DOT__core__DOT__d__DOT__casez_tmp_1);
    return op2;
}
void watch_top(){
    // if (!(top->ysyxSoCFull_top__DOT__inst==WATCH_INST)) return;
    _top = top();
    if (!_top) return;
    // if(top_pc()!=0x800013a0) return; // only watch when pc is 0x80000000
    printf(" io_halt %d ,pc %x,dnpc %x, inst: %.8x, a0 %x alu1 %x, alu2 %x\n",
        top_halt(),
        top_pc(),
        top_dnpc(),
        top_inst(),
        top_gpr(10),
        top_op1(),
        top_op2()
    );
}

 
#endif