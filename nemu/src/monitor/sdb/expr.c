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

/* We use the POSIX regex functions to process regular expressions.
 * Type 'man regex' for more information about POSIX regex functions.
 */
#include <regex.h>
#include <string.h>
enum {
  TK_NOTYPE = 256, TK_EQ,TK_DEC,TK_DEREF,
  TK_NEG,TK_LEFTP,TK_RIGHTP,TK_ADD,TK_SUB,
  TK_MUL,TK_DIV,

  /* TODO: Add more token types */

};

static struct rule {
  const char *regex;
  int token_type;
} rules[] = {

  /* TODO: Add more rules.
   * Pay attention to the precedence level of different rules.
   */

  {" +", TK_NOTYPE},    // spaces
  {"\\+", '+'},         // plus
  {"==", TK_EQ},        // equal
  {"-",'-'},
  {"/",'/'},
  {"\\*",'*'},
  {"\\(",'('},
  {"\\)",')'},
  {"[0-9]+",TK_DEC},
};

#define NR_REGEX ARRLEN(rules)
#define MAX_TOKEN 65536
static regex_t re[NR_REGEX] = {};

/* Rules are used for many times.
 * Therefore we compile them only once before any usage.
 */
void init_regex() {
  int i;
  char error_msg[128];
  int ret;

  for (i = 0; i < NR_REGEX; i ++) {
    ret = regcomp(&re[i], rules[i].regex, REG_EXTENDED);
    if (ret != 0) {
      regerror(ret, &re[i], error_msg, 128);
      panic("regex compilation failed: %s\n%s", error_msg, rules[i].regex);
    }
  }
}

typedef struct token {
  int type;
  char str[32];
} Token;

static Token tokens[MAX_TOKEN] __attribute__((used)) = {};
static int nr_token __attribute__((used))  = 0;

static bool make_token(char *e) {
  int position = 0;
  int i=0;
  regmatch_t pmatch;

  nr_token = 0;

  while (e[position] != '\0') {
    /* Try all rules one by one. */
    for (i = 0; i < NR_REGEX; i ++) {
      if (regexec(&re[i], e + position, 1, &pmatch, 0) == 0 && pmatch.rm_so == 0) {
        char *substr_start = e + position;
        int substr_len = pmatch.rm_eo;

        // Log("match rules[%d] = \"%s\" at position %d with len %d: %.*s",
        //     i, rules[i].regex, position, substr_len, substr_len, substr_start);

        position += substr_len;

        /* TODO: Now a new token is recognized with rules[i]. Add codes
         * to record the token in the array `tokens'. For certain types
         * of tokens, some extra actions should be performed.
         */
        
        if (substr_len>=32){
            printf("length of %s out of 32",substr_start);
            assert(0);
            return false;
        }
        if(rules[i].token_type == TK_NOTYPE) continue; 
        strncpy(tokens[nr_token].str,substr_start,substr_len);

        switch (rules[i].token_type) {
          case TK_DEC: tokens[nr_token].type = TK_DEC; break;
          case TK_EQ: tokens[nr_token].type = TK_EQ; break;
          case '+': tokens[nr_token].type = TK_ADD; break;
          case '/': tokens[nr_token].type = TK_DIV; break;
          case '(': tokens[nr_token].type = TK_LEFTP; break;
          case ')': tokens[nr_token].type = TK_RIGHTP; break;
          case '-': 
              tokens[nr_token].type = TK_SUB; break;
              break;
          case '*':
              tokens[nr_token].type = TK_MUL; break;
              break;
          default: 
            Log("invalid token type %d",rules[i].token_type);
            break;
        }
        nr_token++;
        break;
      }
    }
    if (nr_token>=MAX_TOKEN){
        Log("num of token is limit to %d. Please shrink the expression",MAX_TOKEN);
        return false;
    }

    if (i == NR_REGEX) {
      Log("no match at position %d\n%s\n%*.s^\n", position, e, position, "");
      return false;
    }
  }
  return true;
}

int find_parentheses_match(int p,int q){
    int j=p;
    int count =1;
    for (; j<=q&&count!=0;j++){
      if (tokens[j].type == TK_LEFTP) {
        count++;
      } else if (tokens[j].type == TK_RIGHTP){
          count--;
      }
      if (count ==0){
        break;
      }
      if (count<0){
          Log("parentheses invalid in [%d,%d]",p,q);
      }
    }
    return j;
}


bool check_parentheses(int p,int q){
  int count = 0;
  for (int i=p;i<=q;i++){
     if (tokens[i].type == TK_LEFTP) {
        count++;
     } else if (tokens[i].type == TK_RIGHTP){
        count--;
     }
  }
  if (count!=0){
    Log("diff of parentheses invalid \"(\":%d,  between %d, %d",count,p,q);
    assert(0);
    return false;
  }
  bool res = false;
  if (tokens[p].type == TK_LEFTP && tokens[q].type == TK_RIGHTP){
      int j = find_parentheses_match(p+1,q);
      res = (j==q)?true:false;
  }
  return res;
}


int op_priority (int op){
  int res = -1; 
  switch (op)
  {
  case TK_MUL:
  case TK_DIV:
    res = 5;
    break;
  case TK_SUB:
  case TK_ADD:
    res = 4;
    break;
  default:
    Log("op %d invalid\n",op);
    // assert(0);
    return -1;
  }
  return res;
}
bool op_valid(int op){
  return (op==TK_MUL||op==TK_DIV||op==TK_ADD||op==TK_SUB)?true:false;
}

// if q>p and tokens[a].type== tokens[b].type return b
int op_order(int a,int b){
  int priority_a = op_priority(tokens[a].type);
  int priority_b =  op_priority(tokens[b].type);
  if (priority_a == priority_b) {
    return (a>b)?a:b;
  }  
  return (priority_a < priority_b)?a:b;
}
int main_op_pos(int p,int q){
  int op_pos = -1;
  for (int i=p;i<=q;i++){
    if (tokens[i].type == TK_DEC) {continue;}
    else if (tokens[i].type == TK_LEFTP){
        // num of parentheses must be equal
        i = find_parentheses_match(i+1,q);
    }else if (op_valid(tokens[i].type)){
       op_pos = (op_pos<0)?i:op_order(op_pos,i);
    }else{
      Log("tokens[i].type %d, tokens[op_pos].type %d, i %d, op_pos %d",tokens[i].type, tokens[op_pos].type, i, op_pos);
    }
  }
  assert(op_pos != -1);
  return op_pos;
}

int eval(int p,int q){
  word_t val;
  if (p>q){
    printf("exprssion eval %d>%d ",p,q);
    assert(0);
  } else if(p==q){
    return atoi(tokens[p].str);
  } else if (check_parentheses(p,q)==true) {
    return eval(p+1,q-1);
  } else {
    int op_pos = main_op_pos(p,q);
    int val1 = eval(p,op_pos-1);
    int val2 = eval(op_pos+1,q);
    switch (tokens[op_pos].type) {
          case TK_ADD: val = val1 + val2; break;
          case TK_SUB: val = val1-val2; break;
          case TK_MUL: val = val1*val2; break;
          case TK_DIV: 
            if (val2 == 0) {Log("divide by zero");assert(0);}
            val = val1/val2;
            break;
          default: assert(0);
    }
  }
  return val;
}

word_t expr(char *e, bool *success) {
  if (!make_token(e)) {
    return 0;
  }
  word_t res = eval(0,nr_token-1);    //eval(p,q), p,q should valid.
  memset(tokens,0,32*sizeof(Token));
  nr_token = 0;
  return res;
}
