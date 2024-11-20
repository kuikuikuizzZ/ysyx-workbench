#include <cpu.h>

#define gpr(Index) (top->concat3(ysyx_24100012_top__DOT__regfiles__DOT____Vcellout__x__BRA__,Index,__KET____DOT__x____pinNumber4))
#define ra (top->ysyx_24100012_top__DOT__regfiles__DOT____Vcellout__x__BRA__1__KET____DOT__x____pinNumber4)
#define a0 (top->ysyx_24100012_top__DOT__regfiles__DOT____Vcellout__x__BRA__10__KET____DOT__x____pinNumber4)
#define pc (top->ysyx_24100012_top__DOT__pc)

const char *regs[] = {
  "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
  "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
  "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
  "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
};


CPU_state cpu = {};
Vysyx_24100012_top* top = NULL;
void step() { top->clk = 0; top->eval(); top->clk = 1; top->eval(); }
void reset(int n) { top->rst = 1; while (n --) { step(); } top->rst = 0; }

void init_cpu(int argc ,char** argv){
    // Construct a VerilatedContext to hold simulation time, etc.
    VerilatedContext* contextp = new VerilatedContext;

    // Pass arguments so Verilated code can see them, e.g. $value$plusargs
    // This needs to be called before you create any model
    contextp->commandArgs(argc, argv);

    // Construct the Verilated model, from Vtop.h generated from Verilating "top.v"
    top = new Vysyx_24100012_top{contextp};
    reset(1);
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
    printf("npc: %s at pc = %.8x", gpr(10)?"***HIT BAD TRAP***":"***HIT GOOD TRAP***",pc );
    return a0;
}

void exec_once(){
    step();
    // Evaluate model
    watch_top();
    #ifdef SDB_ENABLE
    cpu.pc = pc;

    #endif
    return;
}

void trace_and_difftest(){
    return;
}

void execute(u_int64_t n){
    for(;n>0;n--){
        exec_once();
        trace_and_difftest();
    }
}

void free_cpu(){
    // Final model cleanup
    top->final();
    // Destroy model
    delete top;
}

int cpu_exec(uint64_t n){
    watch_top();
    execute(n);
    int ret = check_halt();
    
    return ret;
}