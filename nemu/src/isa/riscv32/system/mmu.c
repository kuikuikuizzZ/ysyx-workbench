/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
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

#include <isa.h>
#include <memory/vaddr.h>
#include <memory/paddr.h>

paddr_t isa_mmu_translate(vaddr_t vaddr, int len, int type) {
  switch (isa_mmu_check(vaddr,len,type)){
    case MMU_FAIL: return MEM_RET_FAIL; 
    case MMU_DIRECT: return vaddr;
    case MMU_TRANSLATE: break;
  };
  paddr_t pt1_addr = (csr[STAP]<<10) & ~0x3ff; // TODO: add real translation here
  // page table walk
  paddr_t pte1_addr = pt1_addr | (vaddr>>22 & 0x3ff);
  word_t pte1 = *pte1_addr;
  assert(pte1 & 0x1,"Invalid page table 1 entry: " FMT_PADDR, vaddr); // valid bit
  paddr_t pt0_addr = (pte1 & ~0x3ff);
  paddr_t pte0_addr = pt0_addr | (vaddr>>12 & 0x3ff);
  word_t  pte0 = *pte0_addr;
  assert(pte0 & 0x1,"Invalid page table 0 entry: " FMT_PADDR, vaddr); // valid bit
  paddr_t paddr = (pte0 & ~0xfff) | (vaddr & 0xfff);
  return paddr;
}
  
