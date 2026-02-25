/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NPC is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include <dlfcn.h>
#include <isa.h>
#include <cpu/cpu.h>
#include <cpu/top.h>
#include <utils.h>
#include <npc.h>
#include <memory.h>
#include <cpu/top.h>
#include <cpu/difftest.h>

void memory_access_skip_ref();

#ifdef CONFIG_DIFFTEST
extern CPU_state cpu;

void ref_reg_display(const diff_context *ref);
void isa_reg_display();
// 双发射版本的数据结构
typedef struct {
    bool ready;
    vaddr_t pc;
    // vaddr_t next_pc;
    int skip_dut_cnt;
} diff_channel_t;

static struct {
    bool is_skip_ref;
    bool has_bubble;
    diff_channel_t ch[2];  // 两个发射通道
    uint64_t inst_count;
} diff_state = {0};

// 双发射检查函数
bool isa_difftest_checkregs_o2(const diff_context *ref_r, vaddr_t retireA_pc, vaddr_t retireB_pc, 
                               bool readyA, bool readyB, vaddr_t next_pc) {
    bool result = true;
    
    // 检查PC：参考模型执行了对应数量的指令后，PC应该等于next_pc
    int retire_count = (readyA ? 1 : 0) + (readyB ? 1 : 0);
    if (retire_count > 0 && ref_r->pc != next_pc) {
        printf("checkreg pc, ref %.8x, top next_pc %.8x, readyA %d , readyB %d, retireA:%.8x, retireB:%.8x\n", 
               ref_r->pc, next_pc, readyA, readyB, retireA_pc, retireB_pc);
        result = false;
    }
    
    // 寄存器检查
    for (int i = 0; i < gpr_size; i++) {
        if (ref_r->gpr[i] != cpu.gpr[i]) {
            printf("checkreg x%d, ref %.8x, top %.8x \n", i, ref_r->gpr[i], cpu.gpr[i]);
            result = false;
            break;
        }
    }
    
    // 内存访问检查（如果需要）
    // mem_access_t mem = top_lsu_state();
    // if (ref_r->mem_addr != mem.addr ||
    //     !mem_data_equal(ref_r->mem_data, mem.data, mem.typ)) {
    //     printf("mem_access_addr, ref %.8x, top %.8x \n", ref_r->mem_addr, mem.addr);
    //     printf("mem_access_data, ref %.8x, top %.8x \n", ref_r->mem_data, mem.data);
    //     result = false;
    // }
    
    return result;
}
void memory_access_skip_ref_o2(vaddr_t mem_addr, bool mem_val) {
    // 这里可以根据具体的MMIO地址范围来判断是否需要跳过检查
    // 例如，如果0x10000000-0x10000FFF是MMIO地址范围：
    if (mem_val && (mem_addr >= 0x10000000 && mem_addr <= 0x10000FFF)) {
        diff_state.is_skip_ref = true;  // 只有当有有效的内存访问时才跳过检查
    }
  // IFDEF(CONFIG_HAS_UART,diff_state.is_skip_ref = diff_state.is_skip_ref || in_uart(mem_addr) );
}

void difftest_skip_ref_o2(bool skip_a, bool skip_b) {
    // if (skip_a) diff_state.ch[0].is_skip_ref = true;
    // if (skip_b) diff_state.ch[1].is_skip_ref = true;
    
    if (skip_a && skip_b) {
        diff_state.ch[0].skip_dut_cnt = 0;
        diff_state.ch[1].skip_dut_cnt = 0;
    }
}

void difftest_skip_dut_o2(int nr_ref_a, int nr_ref_b, int nr_dut_a, int nr_dut_b) {
    diff_state.ch[0].skip_dut_cnt += nr_dut_a;
    diff_state.ch[1].skip_dut_cnt += nr_dut_b;
    
    while (nr_ref_a-- > 0) {
        ref_difftest_exec(1);
    }
    while (nr_ref_b-- > 0) {
        ref_difftest_exec(1);
    }
}

static void checkregs_o2(const diff_context *ref, vaddr_t retireA_pc, vaddr_t retireB_pc, 
                         bool readyA, bool readyB, vaddr_t next_pc) {
    if (!isa_difftest_checkregs_o2(ref, retireA_pc, retireB_pc, readyA, readyB, next_pc)) {
        npc_state.state = NPC_ABORT;
        npc_state.halt_pc = readyA ? retireA_pc : retireB_pc;
        isa_reg_display();
        ref_reg_display(ref);
    }
}

// 更新接口函数名以区别于原来的单发射版本
void difftest_step_o2(vaddr_t pc,vaddr_t next_pc) {
    diff_context ref_r;
    retire_info_t retire_info = top_retire_info(); // 获取top模块的退休信息
    uint32_t retireA_pc = retire_info.retireA_pc;
    uint32_t retireB_pc = retire_info.retireB_pc;
    bool readyA = retire_info.readyA ;
    bool readyB = retire_info.readyB;

    // 获取本周期退休指令数
    int retire_count = (readyA ? 1 : 0) + (readyB ? 1 : 0);
    
    // 处理skip dut逻辑（针对每个通道）
    // for (int i = 0; i < 2; i++) {
    //     vaddr_t retire_pc = (i == 0) ? retireA_pc : retireB_pc;
    //     bool ready = (i == 0) ? readyA : readyB;
        
    //     if (ready && diff_state.ch[i].skip_dut_cnt > 0) {
    //         ref_difftest_regcpy(&ref_r, DIFFTEST_TO_DUT);
            
    //         // 检查当前PC是否已经追上了参考模型
    //         if (ref_r.pc == retire_pc + 4) {  // 假设指令长度为4字节
    //             diff_state.ch[i].skip_dut_cnt = 0;
    //         } else {
    //             diff_state.ch[i].skip_dut_cnt--;
    //             if (diff_state.ch[i].skip_dut_cnt == 0) {
    //                 panic("Channel %d can not catch up with ref.pc = " FMT_WORD " at pc = " FMT_WORD, 
    //                       i, ref_r.pc, retire_pc);
    //             }
    //             return;  // 跳过检查
    //         }
    //     }
    // }
    
    // NOTE: access MMIO may cause some side effect, thus skip the checking of instructions with MMIO access.
    memory_access_skip_ref_o2(retire_info.mem_addr, retire_info.mem_val);
    
    if (diff_state.is_skip_ref) {
      // to skip the checking of an instruction, just copy the reg state to reference design
      CPU_state temp_cpu;
      temp_cpu = cpu;
      temp_cpu.pc = readyB ? retire_info.next_retire_pc : retireB_pc;
      ref_difftest_regcpy(&temp_cpu, DIFFTEST_TO_REF);
    }
  
    
    // 只有当有指令退休时才进行检查
    if (retire_count > 0 ) {  
        if (diff_state.is_skip_ref) {
          diff_state.is_skip_ref = false;
          return;
        }
        // 让参考模型执行对应数量的指令
        ref_difftest_exec(retire_count);
        
        // 获取参考模型状态
        ref_difftest_regcpy(&ref_r, DIFFTEST_TO_DUT);

        // 进行检查
        checkregs_o2(&ref_r, retireA_pc, retireB_pc, readyA, readyB, retire_info.next_retire_pc);
        // 更新状态
        diff_state.ch[0].pc = retireA_pc;
        diff_state.ch[1].pc = retireB_pc;
        diff_state.ch[0].ready = readyA;
        diff_state.ch[1].ready = readyB;
        diff_state.inst_count += retire_count;
    }
    return;
}
#else

#endif

