#include <am.h>
#include <klib.h>
#include <klib-macros.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)
static unsigned long int next = 1;

int rand(void) {
  // RAND_MAX assumed to be 32767
  next = next * 1103515245 + 12345;
  return (unsigned int)(next/65536) % 32768;
}

void srand(unsigned int seed) {
  next = seed;
}

int abs(int x) {
  return (x < 0 ? -x : x);
}

int atoi(const char* nptr) {
  int x = 0;
  while (*nptr == ' ') { nptr ++; }
  while (*nptr >= '0' && *nptr <= '9') {
    x = x * 10 + *nptr - '0';
    nptr ++;
  }
  return x;
}

void reverse(char s[]){
  int n = strlen(s);
  int i,j,c;
  for (i=0,j=n-1;i<j;i++,j--){
    c = s[i];
    s[i] = s[j];
    s[j] = c;
  }
}

void itoa(int n , char s[]){
  int sign;
  if ((sign=n)<0){
    n = -n;
  }
  int i=0;
  do{
    s[i++] = n%10+'0';
  }while ((n/=10)>0);
  if (sign<0){
    s[i++]='-';
  }
  s[i] = '\0';
  reverse(s);
}

void *malloc(size_t size) {
  // On native, malloc() will be called during initializaion of C runtime.
  // Therefore do not call panic() here, else it will yield a dead recursion:
  //   panic() -> putchar() -> (glibc) -> malloc() -> panic()
#if !(defined(__ISA_NATIVE__) && defined(__NATIVE_USE_KLIB__))
  panic("Not implemented");
#endif
  return NULL;
}

void free(void *ptr) {
}

#endif
