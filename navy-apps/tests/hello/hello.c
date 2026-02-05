#include <unistd.h>
#include <stdio.h>

int main(int argc, char *argv[], char *envp[]) {
  write(1, "Hello World!\n", 13);
  int i = 2;
  volatile int j = 0;
  while (1) {
    j ++;
    if (j == 1000000) {
      printf("Hello World from Navy-apps args %d %s env %s for the %dth time!\n",argc, argv[0], envp[0], i ++) ;
      j = 0;
    }
  }
  return 0;
}
