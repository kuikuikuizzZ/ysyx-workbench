

#include <nvboard.h>
#include "VysyxSoCFull.h"
#include <cpu/cpu.h>

#ifdef CONFIG_NPC_CHISEL
#include <cpu/top.h>
#ifndef __DEBUG_TOP__
#define __DEBUG_TOP__
#define LSU_FCN(key) 


static uint32_t pc          = 0;
static uint32_t inst        = 0;
static uint32_t halt        = 0;

static mem_access_t lsu_state = {0};
#
extern "C" void dpi_port(int in_halt, int in_pc, int in_inst){
    pc      = in_pc;
    inst    = in_inst;
    halt    = in_halt;
}

extern "C" void lsu_port(bool en ,bool fcn, int typ, int addr, int data){
    if (en){
        lsu_state.fcn     = fcn;
        lsu_state.addr    = addr;
        lsu_state.data    = data;
        lsu_state.enable  = true;
        lsu_state.typ     = typ;
    }
}
#endif

#ifdef CONFIG_NVBOARD
void nvboard_bind_all_pins(VysyxSoCFull* top) {
	nvboard_bind_pin( &top->externalPins_gpio_in, 16, SW15, SW14, SW13, SW12, SW11, SW10, SW9, SW8, SW7, SW6, SW5, SW4, SW3, SW2, SW1, SW0);
	nvboard_bind_pin( &top->externalPins_gpio_out, 16, LD15, LD14, LD13, LD12, LD11, LD10, LD9, LD8, LD7, LD6, LD5, LD4, LD3, LD2, LD1, LD0);
	nvboard_bind_pin( &top->externalPins_gpio_seg_0, 8, SEG0A, SEG0B, SEG0C, SEG0D, SEG0E, SEG0F, SEG0G, DEC0P);
	nvboard_bind_pin( &top->externalPins_gpio_seg_1, 8, SEG1A, SEG1B, SEG1C, SEG1D, SEG1E, SEG1F, SEG1G, DEC1P);
	nvboard_bind_pin( &top->externalPins_gpio_seg_2, 8, SEG2A, SEG2B, SEG2C, SEG2D, SEG2E, SEG2F, SEG2G, DEC2P);
	nvboard_bind_pin( &top->externalPins_gpio_seg_3, 8, SEG3A, SEG3B, SEG3C, SEG3D, SEG3E, SEG3F, SEG3G, DEC3P);
	nvboard_bind_pin( &top->externalPins_gpio_seg_4, 8, SEG4A, SEG4B, SEG4C, SEG4D, SEG4E, SEG4F, SEG4G, DEC4P);
	nvboard_bind_pin( &top->externalPins_gpio_seg_5, 8, SEG5A, SEG5B, SEG5C, SEG5D, SEG5E, SEG5F, SEG5G, DEC5P);
	nvboard_bind_pin( &top->externalPins_gpio_seg_6, 8, SEG6A, SEG6B, SEG6C, SEG6D, SEG6E, SEG6F, SEG6G, DEC6P);
	nvboard_bind_pin( &top->externalPins_gpio_seg_7, 8, SEG7A, SEG7B, SEG7C, SEG7D, SEG7E, SEG7F, SEG7G, DEC7P);

    nvboard_bind_pin( &top->externalPins_ps2_clk    ,   1,  PS2_CLK);
	nvboard_bind_pin( &top->externalPins_ps2_data   ,   1,  PS2_DAT);
	nvboard_bind_pin( &top->externalPins_vga_r      ,   8,  VGA_R7, VGA_R6, VGA_R5, VGA_R4, VGA_R3, VGA_R2, VGA_R1, VGA_R0 );
	nvboard_bind_pin( &top->externalPins_vga_g      ,   8,  VGA_G7, VGA_G6, VGA_G5, VGA_G4, VGA_G3, VGA_G2, VGA_G1, VGA_G0 );
	nvboard_bind_pin( &top->externalPins_vga_b      ,   8,  VGA_B7, VGA_B6, VGA_B5, VGA_B4, VGA_B3, VGA_B2, VGA_B1, VGA_B0 );
	nvboard_bind_pin( &top->externalPins_vga_hsync  ,   1,  VGA_HSYNC);
	nvboard_bind_pin( &top->externalPins_vga_vsync  ,   1,  VGA_VSYNC );
	nvboard_bind_pin( &top->externalPins_vga_valid  ,   1,  VGA_BLANK_N);
	nvboard_bind_pin( &top->externalPins_uart_rx	,   1,  UART_RX);
	nvboard_bind_pin( &top->externalPins_uart_tx	,   1,  UART_TX);
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


typedef struct {
    uint32_t inst;
    uint32_t pc;
    uint32_t dnpc;
} Watch_top;
Watch_top *wt = NULL;

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
    uint32_t mstatus    = _rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__d__DOT__csr__DOT__reg_mstatus;
    uint32_t mtvec      = _rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__d__DOT__csr__DOT__reg_mtvec;
    uint32_t mepc       = _rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__d__DOT__csr__DOT__reg_mepc;
    uint32_t mcause     = _rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__d__DOT__csr__DOT__reg_mcause;
    uint32_t mtval      = _rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__d__DOT__csr__DOT__reg_mtval;
    uint32_t csrs[5] = {0,mstatus,mtvec,mepc, mcause};  
    return csrs[i]; 
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

mem_access_t top_lsu_state(){
    return lsu_state;
}

void clear_top_lsu_state(){
    lsu_state.addr = 0;
    lsu_state.data = 0;
    lsu_state.enable = 0;
    lsu_state.fcn = 0;
}


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
    if (!wt) {
        wt = new Watch_top;
        wt->inst = top_inst();
        wt->pc = top_pc();
        wt->dnpc =top_dnpc();
    } 
    if (wt->inst==top_inst() && top_pc()==wt->pc && top_dnpc()==wt->dnpc) return;
    else {
        // if(top_pc()!=0x800013a0) return; // only watch when pc is 0x80000000
        printf(" io_halt %d ,pc %.8x,dnpc %.8x, inst: %.8x, a0 %.8x alu1 %.8x, alu2 %.8x, mem_en: %d,r/w %d addr %.8x, data %.8x \n",
            top_halt(),
            top_pc(),
            top_dnpc(),
            top_inst(),
            top_gpr(10),
            top_op1(),
            top_op2(),
            lsu_state.enable,
            lsu_state.fcn,
            lsu_state.addr,
            lsu_state.data
        );
        wt->inst = top_inst();
        wt->pc = top_pc();
        wt->dnpc = top_dnpc();
    }
    
}

 
#endif