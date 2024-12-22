#include <cpu/cpu.h>
#include <cpu/difftest.h>
#include <npc.h>
#include <ringbuffer.h>
#include "verilated_vpi.h"  // Required to get definitions

Vysyx_24100012_top* top = NULL;



const char *regs[] = {
  "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
  "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
  "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
  "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
};

extern Vysyx_24100012_top* top;
CPU_state cpu = {};
char itrace_buff [ITRACE_SIZE];
RingBuffer *rb = NULL;

void init_disasm();
void device_update();
void step() { top->clk = 0; top->eval(); top->clk = 1; top->eval(); }
void reset(int n) { top->rst = 1; while (n --) { step(); } top->rst = 0; }
bool isa_difftest_checkregs(CPU_state *ref_r, vaddr_t pc) {
  if (ref_r->pc != cpu.pc ){
    printf("checkreg pc, ref %x, top %x \n",ref_r->pc,cpu.pc);
    return false;
  }
  for (int i=0;i<gpr_size;i++){
     if(ref_r->gpr[i]!=cpu.gpr[i]) {
        printf("checkreg x%d, ref %x, top %x \n",i,ref_r->gpr[i],cpu.gpr[i]);
        return false;
     }
  }  
  return true;
}
void sync_cpu(){
    for (int i=0;i<gpr_size;i++)
        cpu.gpr[i] = gpr(i);
    cpu.pc = top_pc;
    return;
}

void watch_top(){
    // if (!(top->ysyx_24100012_top__DOT__inst==WATCH_INST)) return;
    printf(" io_halt %d ,pc %x,dnpc %x, inst: %.8x,func3 %d, imm %u, writedata: %x , writeCSR: %x,CSR: %d,alu_a: %x,alu_b: %x, a0 = %x,ra = %x,a%d = %x\n",
        top->io_halt,
        top_pc,
        top_dnpc,
        top->ysyx_24100012_top__DOT__inst,
        top->ysyx_24100012_top__DOT__ALUSel,
        top->ysyx_24100012_top__DOT__imm,
        top->ysyx_24100012_top__DOT__writeData,
        top->ysyx_24100012_top__DOT__AluOut,
        top->ysyx_24100012_top__DOT__csrType,
        top->ysyx_24100012_top__DOT__alu_a,
        top->ysyx_24100012_top__DOT__alu_b,

        gpr(10),
        gpr(1),
        14,
        gpr(14));
}
void init_cpu(int argc ,char** argv){
    // Construct a VerilatedContext to hold simulation time, etc.
    VerilatedContext* contextp = new VerilatedContext;

    // Pass arguments so Verilated code can see them, e.g. $value$plusargs
    // This needs to be called before you create any model
    contextp->commandArgs(argc, argv);

    // Construct the Verilated model, from Vtop.h generated from Verilating "top.v"
    top = new Vysyx_24100012_top{contextp};
    reset(1);
    sync_cpu();
    #ifdef CONFIG_WATCH_TOP
    watch_top();
    #endif
}

void init_itrace(){
    init_disasm();
    rb = RingBuffer_create(LOG_BUFSIZE);
}

void isa_reg_display(){
    printf("npc register: \n");
    for (int i=0;i<gpr_size;i++){
        printf("%4s:%.8x",regs[i],gpr(i));
        (i%3==0)?printf("\n"):printf(" ");
    }
    printf("%4s:%.8x\n","pc",top_pc);
}

void assert_fail_msg() {
  isa_reg_display();
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
    #ifdef CONFIG_WATCH_TOP
    watch_top();
    #endif
    sync_cpu();
    if (top_halt){
        NPCTRAP(top_pc,gpr(10));
    }
    return;
}

void trace_and_difftest(){
    #ifdef CONFIG_DIFFTEST
    difftest_step(cpu.pc,top_dnpc);
    #endif
    #ifdef CONFIG_ITRACE
    itrace_once();
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
        device_update(); 
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
    switch (npc_state.state) {
    case NPC_RUNNING: npc_state.state = NPC_STOP; break;

    case NPC_END: case NPC_ABORT:
      Log("npc: %s at pc = " FMT_WORD,
          (npc_state.state == NPC_ABORT ? ANSI_FMT("ABORT", ANSI_FG_RED) :
           (npc_state.halt_ret == 0 ? ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN) :
            ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED))),
          npc_state.halt_pc);
      // fall through
    case NPC_QUIT: break;;
    }
    return 0;
}