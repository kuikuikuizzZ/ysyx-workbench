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

#include "sdb.h"
#include <memory/vaddr.h>
#include <isa.h>
#define NR_WP 32



static WP wp_pool[NR_WP] = {};
static WP *head = NULL, *free_ = NULL;

void init_wp_pool() {
  int i;
  for (i = 0; i < NR_WP; i ++) {
    wp_pool[i].NO = i;
    wp_pool[i].next = (i == NR_WP - 1 ? NULL : &wp_pool[i + 1]);
    wp_pool[i].prev = (i == 0 ? NULL : &wp_pool[i - 1]);
  } 

  head = NULL;
  free_ = wp_pool;
}

/* TODO: Implement the functionality of watchpoint */
// return watchpoint NO
int new_wp(word_t value, char* args){
    if(!free_) {
      assert(0);
    } 
    WP* temp = free_;
    temp->next = head;
    if (head){
      head->prev = temp;
    }
    head = temp;
    free_ = free_->next;
    temp->value = value;
    char* res = strncpy(temp->args,args,32);
    Assert(res!=NULL,"watch point args: %s invalid",args); 
    return head->NO;
}

void free_wp(WP *wp){
    if (wp->next){
      wp->next->prev = wp->prev; 
    }
    if (wp->prev){
      wp->prev->next = wp->next;
    }
    wp->next = free_;
    if (free_){
      free_->prev = wp;
    }
    free_ = wp;
    return;
}

void delete_wp(int no){
    WP wp = wp_pool[no];
    free_wp(&wp);
    return;
}

int head_wp_no(){
  return head->NO;
}

/* return watchpoint change or not*/
bool wps_diff(){
  WP *cur = head;
  bool change;
  while(cur !=NULL){
      //TODO: should support different expr
      bool success;
      word_t new = isa_reg_str2val(cur->args,&success);
      if (new != cur->value){
          printf("Num: %d, Old %u, New: %u\n",cur->NO,cur->value,new);
          cur->value = new;
          change = true;
      }
      cur = cur->next;
  }
  return change;
}