#include <nterm.h>
#include <stdarg.h>
#include <unistd.h>
#include <SDL.h>

char handle_key(SDL_Event *ev);

static void sh_printf(const char *format, ...) {
  static char buf[256] = {};
  va_list ap;
  va_start(ap, format);
  int len = vsnprintf(buf, 256, format, ap);
  va_end(ap);
  term->write(buf, len);
}

static void sh_banner() {
  sh_printf("Built-in Shell in NTerm (NJU Terminal)\n\n");
}

static void sh_prompt() {
  sh_printf("sh> ");
}

char* tolower(char* str) {
  size_t size = strlen(str);
  char* new_str = (char*)malloc(size);
  strcpy(new_str, str);
  char *p = new_str;
  while (*p) {
    if (*p >= 'A' && *p <= 'Z') {
      *p = *p - 'A' + 'a';
    }
    p++;
  }
  new_str[size-1] = '\0';
  return new_str;
}

static void sh_handle_cmd(const char *cmd) {
  char *cmd_lower = tolower((char*)cmd);
  if (strcmp(cmd_lower, "exit") == 0) {
    printf("Exiting shell...\n");
    exit(0);
  }
  execvp((char*)cmd_lower, NULL);
}

void builtin_sh_run() {
  sh_banner();
  sh_prompt();
  setenv("PATH", "/bin", 0);
  while (1) {
    SDL_Event ev;
    if (SDL_PollEvent(&ev)) {
      if (ev.type == SDL_KEYUP || ev.type == SDL_KEYDOWN) {
        const char *res = term->keypress(handle_key(&ev));
        if (res) {
          printf("cmd: %s\n", res);
          sh_handle_cmd(res);
          sh_prompt();
        }
      }
    }
    refresh_terminal();
  }
}


// static int msh_split(char *cmd, rt_size_t length, char *argv[FINSH_ARG_MAX])
// {
//     char *ptr;
//     rt_size_t position;
//     rt_size_t argc;
//     rt_size_t i;

//     ptr = cmd;
//     position = 0;
//     argc = 0;

//     while (position < length)
//     {
//         /* strip bank and tab */
//         while ((*ptr == ' ' || *ptr == '\t') && position < length)
//         {
//             *ptr = '\0';
//             ptr ++;
//             position ++;
//         }

//         if (argc >= FINSH_ARG_MAX)
//         {
//             rt_kprintf("Too many args ! We only Use:\n");
//             for (i = 0; i < argc; i++)
//             {
//                 rt_kprintf("%s ", argv[i]);
//             }
//             rt_kprintf("\n");
//             break;
//         }

//         if (position >= length) break;

//         /* handle string */
//         if (*ptr == '"')
//         {
//             ptr ++;
//             position ++;
//             argv[argc] = ptr;
//             argc ++;

//             /* skip this string */
//             while (*ptr != '"' && position < length)
//             {
//                 if (*ptr == '\\')
//                 {
//                     if (*(ptr + 1) == '"')
//                     {
//                         ptr ++;
//                         position ++;
//                     }
//                 }
//                 ptr ++;
//                 position ++;
//             }
//             if (position >= length) break;

//             /* skip '"' */
//             *ptr = '\0';
//             ptr ++;
//             position ++;
//         }
//         else
//         {
//             argv[argc] = ptr;
//             argc ++;
//             while ((*ptr != ' ' && *ptr != '\t') && position < length)
//             {
//                 ptr ++;
//                 position ++;
//             }
//             if (position >= length) break;
//         }
//     }

//     return argc;
// }