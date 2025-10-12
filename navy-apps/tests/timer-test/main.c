#include <stdio.h>
#include <assert.h>
#include <time.h>
#include <NDL.h>
int main() {
  // struct timeval tv;
  // size_t count = 10; 
  // int second = 0;
  // while(count){
  //   gettimeofday(&tv, NULL);
  //   if (tv.tv_sec > second ) {
  //     printf("time: %d seconds \n", tv.tv_sec);
  //     count --;
  //     second = tv.tv_sec;
  //   }
  // }
  size_t count = 10; 
  int second = 0;
  while(count){
    uint32_t ms = NDL_GetTicks();
    if (ms/1000 > second ) {
      printf("time: %d seconds \n",ms/1000);
      count --;
      second = ms/1000;
    }
  }
  printf("PASS!!!\n");

  return 0;
}
