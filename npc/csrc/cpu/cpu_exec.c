#include <cpu.h>
#include <npc.h>
#include <ringbuffer.h>
#include "verilated_vpi.h"  // Required to get definitions

#define gpr(i) (top->ysyx_24100012_top__DOT__regfiles__DOT__reg_output_list[i])
#define top_pc (top->ysyx_24100012_top__DOT__pc)
#define top_halt (top->io_halt)
#define top_inst (top->ysyx_24100012_top__DOT__inst)


const char *regs[] = {
  "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
  "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
  "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
  "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
};


CPU_state cpu = {};
Vysyx_24100012_top* top = NULL;
char itrace_buff [ITRACE_SIZE];
RingBuffer *rb = NULL;

void init_disasm();
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

void init_itrace(){
    init_disasm();
    rb = RingBuffer_create(LOG_BUFSIZE);
}

void isa_reg_display(){
    for (int i=0;i<RV_NR;i++){
        printf("%4s:%.8x",regs[i],gpr(i));
        (i%3==0)?printf("\n"):printf(" ");
    }
    printf("%4s:%.8x\n","pc",top_pc);
}

void assert_fail_msg() {
  isa_reg_display();
}


void watch_top(){
    printf(" io_halt %d ,pc %x,pcsel: %d, inst: %.8x, imm %d,rs1: %d a0 = %x,ra = %x\n",
        top->io_halt,
        top_pc,
        top->ysyx_24100012_top__DOT__PCSel,
        top->ysyx_24100012_top__DOT__inst,
        top->ysyx_24100012_top__DOT__imm,
        top->ysyx_24100012_top__DOT__rs1,
        gpr(10),
        gpr(1));
}

int check_halt(){
    printf("npc: %s at pc = %.8x\n", gpr(10)?"***HIT BAD TRAP***":"***HIT GOOD TRAP***",top_pc );
    return gpr(10);
}

void itrace_once(){
  // 32 match inst name in capstone define
  char inst_name[32];
  char *p = itrace_buff;
  p +=  (npc_state.state != NPC_ABORT) ? 
      snprintf(p, ITRACE_SIZE,"    " FMT_WORD ":", top_pc):
      snprintf(p, ITRACE_SIZE," -->" FMT_WORD ":", top_pc);
  int ilen = sizeof(word_t);
  int i;
  uint8_t *inst = (uint8_t *)&top_inst;
  for (i = ilen - 1; i >= 0; i --) {
    p += snprintf(p, 4, " %02x", inst[i]);
  }
  int ilen_max = MUXDEF(CONFIG_ISA_x86, 8, 4);
  int space_len = ilen_max - ilen;
  if (space_len < 0) space_len = 0;
  space_len = space_len * 3 + 1;
  memset(p, ' ', space_len);
  p += space_len;
  void disassemble(char *str, int size, uint64_t pc, uint8_t *code, int nbyte,char* inst);
  disassemble(p, itrace_buff + LOG_BUFSIZE - p, top_pc, (uint8_t *)&top_inst, ilen,inst_name);
  int len = strlen(itrace_buff);
  memset(itrace_buff+len,' ',ITRACE_SIZE-len);
  itrace_buff[ITRACE_SIZE-1] = '\n';
  if(RingBuffer_available(rb)<ITRACE_SIZE){
    RingBuffer_commit_read(rb,ITRACE_SIZE);
  }
  Assert(ITRACE_SIZE>(len+1),"length of itrace excceed\n");
  RingBuffer_put(rb,itrace_buff,ITRACE_SIZE);
  memset(itrace_buff,0,ITRACE_SIZE);
}

void exec_once(){
    step();
    // Evaluate model
    // watch_top();
    #ifdef CONFIG_ITRACE
    itrace_once();
    #endif
    if (top_halt){
        NPCTRAP(top_pc,gpr(10));
        check_halt();
    }
    return;
}

void trace_and_difftest(){
    #ifdef CONFIG_ITRACE
    if ((npc_state.state!=NPC_RUNNING)) {
        char temp_buf [LOG_BUFSIZE];
        RingBuffer_get(rb,temp_buf,LOG_BUFSIZE);
        log_write("%s", temp_buf); 
    }
    #endif
    return;
}

void execute(u_int64_t n){
    for(;n>0;n--){
        exec_once();
        trace_and_difftest();
        if (npc_state.state != NPC_RUNNING) break;
    }
}

void free_cpu(){
    // Final model cleanup
    top->final();
    // Destroy model
    delete top;
}

int cpu_exec(uint64_t n){
    switch (npc_state.state) {
        case NPC_END: case NPC_ABORT: case NPC_QUIT:
            printf("Program execution has ended. To restart the program, exit NPC and run again.\n");
            return 0;
        default: npc_state.state = NPC_RUNNING;
    }
    // watch_top();
    execute(n);    
    return 0;
}