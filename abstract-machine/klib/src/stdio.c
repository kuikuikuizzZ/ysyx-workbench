#include <am.h>
#include <klib.h>
#include <klib-macros.h>
#include <stdarg.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

int printf(const char *fmt, ...) {
  int i = 0;
  int rc = 0;
  va_list argp;
  va_start(argp,fmt);

  for (i=0;fmt[i] != '\0';i++){
      if (fmt[i]=='%'){
        i++;
        switch (fmt[i])
        {
        case 'd':
          int *out_int = va_arg(argp,int *); 
          char s[12];
          itoa(*out_int,s);
          putstr(s);
          rc+= strlen(s);
          break;
        case 's':
          char** out_string = va_arg(argp,char**);
          putstr(*out_string);
          rc+= strlen(*out_string);
          break;
        default:
          break;
        }
      } else{
         putch(fmt[i]);
         rc++;
      }
  }
  va_end(argp);
  return rc;
}

int vsprintf(char *out, const char *fmt, va_list argp) {
  panic("Not implemented");
}

int sprintf(char *out, const char *fmt, ...) {
  int i = 0;
  int rc = 0;
  int len =0;
  va_list argp;
  va_start(argp,fmt);

  for (i=0;fmt[i] != '\0';i++){
      if (fmt[i]=='%'){
        i++;
        switch (fmt[i])
        {
        case 'd':
          int out_int = va_arg(argp,int ); 
          char s[12];
          itoa(out_int,s);
          len = strlen(s);
          strncpy(&out[rc],s,len);
          rc+= len;
          break;
        case 's':
          char* out_string = va_arg(argp,char*);
          len = strlen(out_string);
          strncpy(&out[rc],out_string,len);
          rc+= len;
          break;
        default:
          break;
        }
      } else{
         strncpy(&out[rc],fmt+i,1);
         rc++;
      }
  }
  out[rc]='\0';
  va_end(argp);
  return rc;
}

int snprintf(char *out, size_t n, const char *fmt, ...) {
  panic("Not implemented");
}

int vsnprintf(char *out, size_t n, const char *fmt, va_list ap) {
  panic("Not implemented");
}

#endif
