#include <verilated.h>
#include "Vysyx_24100012_top.h"
#include <assert.h>
#include <memory.h>
#include <stdlib.h>
#include <stdio.h>

#define a0 top->ysyx_24100012_top__DOT__regfiles__DOT____Vcellout__x__BRA__10__KET____DOT__x____pinNumber4
#define ra top->ysyx_24100012_top__DOT__regfiles__DOT____Vcellout__x__BRA__1__KET____DOT__x____pinNumber4
#define pc top->ysyx_24100012_top__DOT__pc
Vysyx_24100012_top* top = NULL;
void step() { top->clk = 0; top->eval(); top->clk = 1; top->eval(); }
void reset(int n) { top->rst = 1; while (n --) { step(); } top->rst = 0; }
void load_prog(const char *img_file) {
    if (!img_file){
        printf("Use default img\n");
        return;
    }
    printf("use image: %s \n",img_file);
    FILE *fp = fopen(img_file, "rb");
    fseek(fp, 0, SEEK_END);
    long size = ftell(fp);
    fseek(fp, 0, SEEK_SET);
    int ret = fread(guest_to_host(MBASE), 1, size, fp);
    assert(ret == size);

    fclose(fp);
}

void watch_top(){
    printf(" io_halt %d ,pc %x,pcsel: %d, inst: %.8x, imm %d,rs1: %d a0 = %x,ra = %x\n",
        top->io_halt,
        pc,
        top->ysyx_24100012_top__DOT__PCSel,
        top->ysyx_24100012_top__DOT__inst,
        top->ysyx_24100012_top__DOT__imm,
        top->ysyx_24100012_top__DOT__rs1,
        a0,
        ra);
}

int check_halt(){
    printf("npc: %s at pc = %.8x", a0?"***HIT BAD TRAP***":"***HIT GOOD TRAP***",pc );
    return a0;
}

int main(int argc, char** argv) {
    init_memory();
    init_isa();
    // See a similar example walkthrough in the verilator manpage.

    // This is intended to be a minimal example.  Before copying this to start a
    // real project, it is better to start with a more complete example,
    // e.g. examples/c_tracing.

    // Construct a VerilatedContext to hold simulation time, etc.
    VerilatedContext* contextp = new VerilatedContext;

    // Pass arguments so Verilated code can see them, e.g. $value$plusargs
    // This needs to be called before you create any model
    contextp->commandArgs(argc, argv);

    // Construct the Verilated model, from Vtop.h generated from Verilating "top.v"
    top = new Vysyx_24100012_top{contextp};
    load_prog(argv[1]);
    reset(1);
    watch_top();
    // Simulate until $finish
    // for(int i=0;i<20;i++) {
    while(!top->io_halt) {
        step();
        // Evaluate model
        watch_top();
    }
    int ret = check_halt();
    // Final model cleanup
    top->final();

    // Destroy model
    delete top;

    // Return good completion status
    return ret;
}
