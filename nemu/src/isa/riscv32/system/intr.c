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
#include <cpu/decode.h>
#include "../local-include/reg.h"

word_t isa_raise_intr(word_t NO, vaddr_t epc, char* is_exception) {
  /* TODO: Trigger an interrupt/exception with ``NO''.
   * Then return the address of the interrupt/exception vector.
   */
  csr(MEPC) = epc;
  csr(MCAUSE) = 0xb;      // Exception Code for Environment call from U-mode
  *is_exception = 1;
  return csr(MTVEC);
}

word_t isa_query_intr() {
  return INTR_EMPTY;
}

void display_exception_info(Decode *s){
  // Display the exception information
  printf("MEPC: 0x%x\n", csr(MEPC));
  printf("MCAUSE: 0x%x\n", csr(MCAUSE));
  printf("MTVEC: 0x%x\n", csr(MTVEC));
  s->is_exception = 0; // Set the exception flag
  // Add more information as needed
}
