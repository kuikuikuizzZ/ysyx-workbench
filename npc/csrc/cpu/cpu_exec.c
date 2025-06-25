#include <cpu/cpu.h>
#include <cpu/top.h>
#include <cpu/difftest.h>
#include <npc.h>
#include <ringbuffer.h>




CPU_state cpu = {};
char itrace_buff [ITRACE_SIZE];
RingBuffer *rb = NULL;

void init_disasm();
void device_update();
bool wps_diff();

void step() {
    IFDEF(CONFIG_NPC_CHISEL,top()->clock = 0);
    IFDEF(CONFIG_NPC_VERILOG,top()->clk = 0);
    top()->eval(); 
    IFDEF(CONFIG_NPC_CHISEL,top()->clock = 1);
    IFDEF(CONFIG_NPC_VERILOG,top()->clk = 1); 
    top()->eval(); }
void reset(int n) { 
    IFDEF(CONFIG_NPC_CHISEL,top()->reset = 1);
    IFDEF(CONFIG_NPC_VERILOG,top()->rst = 1);
    while (n --) { step(); } 
    IFDEF(CONFIG_NPC_CHISEL,top()->reset = 0);
    IFDEF(CONFIG_NPC_VERILOG,top()->rst = 0);}
void sync_cpu(){
    for (int i=0;i<gpr_size;i++)
        cpu.gpr[i] = top_gpr(i);
    cpu.pc = top_pc();
    return;
}

void init_cpu(int argc ,char** argv){
    // Construct a VerilatedContext to hold simulation time, etc.
    VerilatedContext* contextp = new VerilatedContext;

    // Pass arguments so Verilated code can see them, e.g. $value$plusargs
    // This needs to be called before you create any model
    contextp->commandArgs(argc, argv);

    // Construct the Verilated model, from Vtop.h generated from Verilating "top.v"
    reset(1);
    #ifdef CONFIG_WATCH_TOP
    watch_top();
    #endif
    sync_cpu();
}

void init_itrace(){
    init_disasm();
    rb = RingBuffer_create(LOG_BUFSIZE);
}

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

void assert_fail_msg() {
  isa_reg_display();
}


int check_halt(){
    printf("npc: %s at pc = %.8x\n", top_gpr(10)?"***HIT BAD TRAP***":"***HIT GOOD TRAP***",top_pc() );
    return top_gpr(10);
}

void itrace_once(Decode * s) {
    // 32 match inst name in capstone define
    char inst_name[32];
    char *p = itrace_buff;
    p +=  (npc_state.state != NPC_ABORT) ? 
        snprintf(p, ITRACE_SIZE,"    " FMT_WORD ":", s->pc):
        snprintf(p, ITRACE_SIZE," -->" FMT_WORD ":", s->pc);
    int ilen = sizeof(word_t);
    int i;
    uint32_t inst = s->inst;
    
    uint8_t *inst_ptr = (uint8_t *)(&inst);
    for (i = ilen - 1; i >= 0; i --) {
        p += snprintf(p, 4, " %02x", *(inst_ptr+i));
    }

    int ilen_max = MUXDEF(CONFIG_ISA_x86, 8, 4);
    int space_len = ilen_max - ilen;
    if (space_len < 0) space_len = 0;
    space_len = space_len * 3 + 1;
    memset(p, ' ', space_len);
    p += space_len;
    void disassemble(char *str, int size, uint64_t pc, uint8_t *code, int nbyte,char* inst);
    disassemble(p, itrace_buff + LOG_BUFSIZE - p, s->pc, inst_ptr, ilen,inst_name);
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


void exec_once(Decode *s){
    s->pc = top_pc();
    step();
    #ifdef CONFIG_WATCH_TOP
    watch_top();
    #endif
    sync_cpu();
    s->inst = top_inst();
    if (top_halt()){
        NPCTRAP(top_pc(),top_gpr(10));
    }
    return;
}

void trace_and_difftest(Decode* s, vaddr_t dnpc){
    #ifdef CONFIG_DIFFTEST
    difftest_step(s->pc,dnpc);
    #endif
    #ifdef CONFIG_ITRACE
    itrace_once(s);
    if ((npc_state.state!=NPC_RUNNING)) {
        char temp_buf [LOG_BUFSIZE];
        RingBuffer_get(rb,temp_buf,LOG_BUFSIZE);
        log_write("%s", temp_buf); 
    }
    #endif
    #ifdef CONFIG_WATCHPOINT
    if (npc_state.state != NPC_END && wps_diff()){

        npc_state.state = NPC_STOP;
    }
    #endif
    return;
}

void execute(u_int64_t n){
    Decode s;
    for(;n>0;n--){
        exec_once(&s);
        trace_and_difftest(&s,cpu.pc);
        if (npc_state.state != NPC_RUNNING) break;
        IFDEF(CONFIG_DEVICE,device_update()); 
    }
}

void free_cpu(){
    // Final model cleanup
    top()->final();
    // Destroy model
    delete_top();
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