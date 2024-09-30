// #include <nvboard.h>
#include <stdlib.h>
#include <verilated.h>
#include "Vshifter8.h"
#include <stdio.h>

// static TOP_NAME dut;

// void nvboard_bind_all_pins(TOP_NAME* top);

bool shift(int16_t num){
    return (num&1)^((num>>2)&1)^((num>>3)&1)^((num>>4)&1);
}

int main() {
  Vshifter8* dut = new Vshifter8;
  dut->rst = 1;
  int16_t sw = rand()%256;
  int16_t last = sw;
  for (int i=0;i<514;i++) {
      dut->sw = sw;
      dut->clk ^= 1;
      dut->eval();
      if (dut->clk==1) {
        printf("clk: %d, rst: %d, sw: %08b, num %08b, shift %b \n",i,dut->rst,
              dut->sw,dut->num,shift(last));
      }
      dut->rst =i<3;
      last = dut->num;
  }
  // nvboard_quit();
}
