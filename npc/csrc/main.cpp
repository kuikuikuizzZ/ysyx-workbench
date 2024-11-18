#include <verilated.h>
#include "Vysyx_24100012_top.h"
#include <assert.h>

// this is not consistent with uint8_t
// but it is ok since we do not access the array directly
static const uint32_t img [] = {
          0x00108513,      //addi a0 x0 1
          0x00150513,      //addi a0 a0 1
          0x00150513,      //addi a0 a0 1
          0x00150513,      //addi a0 a0 1
          0x00150513,      //addi a0 a0 1
          0x00150513,      //addi a0 a0 1
          0x00108073,      //ebreak
};

Vysyx_24100012_top* top = NULL;
void step() { top->clk = 0; top->eval(); top->clk = 1; top->eval(); }
void reset(int n) { top->rst = 1; while (n --) { step(); } top->rst = 0; }
void load_prog(const char *img_file) {
    if (!img_file){
        memcpy(&top->ysyx_24100012_top__DOT__ifu__DOT__mem__DOT__ram, img, sizeof(img));
    }
    FILE *fp = fopen(img_file, "rb");
    fseek(fp, 0, SEEK_END);
    long size = ftell(fp);
    int ret = fread(&top->ysyx_24100012_top__DOT__ifu__DOT__mem__DOT__ram, 1, size, fp);
    assert(ret == 1);

    fclose(fp);
}

int main(int argc, char** argv) {
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

    // Simulate until $finish
    while(!top->io_halt) {
        step();
        printf(" io_halt %d ,pc %x, inst: %x, rs1 %d,imm %d rd: %d, wen %d,alu:%d  a0 = %d\n",
            top->io_halt,
            top->ysyx_24100012_top__DOT__ifu__DOT__pc,
            top->ysyx_24100012_top__DOT__inst,
            top->ysyx_24100012_top__DOT__rs1,
            top->ysyx_24100012_top__DOT__imm,
            top->ysyx_24100012_top__DOT__rd,
            top->ysyx_24100012_top__DOT__wen,
            top->ysyx_24100012_top__DOT__alu_sel,
            // top->ysyx_24100012_top__DOT__alu_out,
            top->ysyx_24100012_top__DOT__regfiles__DOT____Vcellout__x__BRA__10__KET____DOT__x____pinNumber4);
        // Evaluate model
    }

    // Final model cleanup
    top->final();

    // Destroy model
    delete top;

    // Return good completion status
    return 0;
}
