#include <klib.h>
#include <klib-macros.h>
#include <stdint.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

size_t strlen(const char *s) {
  if (s == NULL){
    return 0;
  }
  int i=0;
  do {
    i++;
  } while (s[i] != '\0');  
  return i;
}

char *strcpy(char *dst, const char *src) {
  char * res = dst;
  while((*dst++=*src++))
    ;
  return res;
}

char *strncpy(char *dst, const char *src, size_t n) {
  int i=0;
  char * res = dst;
  while (i<n && (*dst++=*src++))
  {
    i++;
  }
  return res;
}

char *strcat(char *dst, const char *src) {
    int l1 = strlen(dst);
    strcpy(&dst[l1],src);
    return dst;
}

int strcmp(const char *s1, const char *s2) {
    int i = 0;
    for (i=0;s1[i]==s2[i];i++){
      if (s1[i] == '\0') {
        return 0;
      }
    }
    return s1[i]-s2[i];
}

int strncmp(const char *s1, const char *s2, size_t n) {
  int i = 0;
  for (i=0;(i<n)&&(s1[i]==s2[i]);i++){
    if (s1[i] == '\0') {
      return 0;
    }
  }
  return s1[i]-s2[i];
}

void *memset(void *s, int c, size_t n) {
    char * ss = (char*)s;
    for (int i=0;i<n;i++){
      ss[i]=(char)c;
    }
    return s;
}

void *memmove(void *dst, const void *src, size_t n) {
  char *s1 = dst;
  const char *s2 = src;
  strncpy(s1,s2,n);
  return s1;
}

void *memcpy(void *out, const void *in, size_t n) {
  char *s1 = out;
  const char *s2 = in;
  strncpy(s1,s2,n);
  return s1;
}

int memcmp(const void *s1, const void *s2, size_t n) {
  const char *ss1 = s1;
  const char *ss2 = s2;
  int i = 0;
  for (i=0;ss1[i]==ss2[i];i++){
    if (i==(n-1)) {
      return 0;
    }
  }
  return ss1[i]-ss2[i];
}

#endif
