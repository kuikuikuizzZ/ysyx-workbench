
#include <cpu/cpu.h>
#ifdef CONFIG_NPC_CHISEL
#include <cpu/top.h>

Top* _top = NULL;
Top_rootp* _rootp =NULL;
Top* top() {
    if (!_top) {
        _top =  new Top{};
        _rootp = _top->rootp;
    }
    return _top;
}

void watch_top(){
    // if (!(top->ysyx_24100012_top__DOT__inst==WATCH_INST)) return;
    _top = top();
    if (!_top) return;
    if(top_pc()!=0x800013a0) return; // only watch when pc is 0x80000000
    printf("watch top");
}

uint32_t top_gpr(int i) {
    if (!_top) return 0;
    if (i < 0 || i >= gpr_size) {
        printf("gpr index %d out of range\n", i);
        return 0;
    }
    return _rootp->Top__DOT__core__DOT__d__DOT__regfile_ext__DOT__Memory[i];
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
    if (!_top) return 0;
    return _rootp->Top__DOT__core__DOT__d__DOT__pc_reg;
}

uint32_t top_halt(){
    if (!_top) return 0;
    return 0;
}

uint32_t top_inst() {
    if (!_top) return 0;
    return _rootp->Top__DOT___memory_io_core_ports_1_resp_bits_data;
}
uint32_t top_dnpc() {
    if (!_top) return 0;
    return 0;
}

void delete_top() {
    if (_top) {
        delete _top ;
    }
}
#endif