#define SDL_malloc  malloc
#define SDL_free    free
#define SDL_realloc realloc

#define SDL_STBIMAGE_IMPLEMENTATION
#include "SDL_stbimage.h"

SDL_Surface* IMG_Load_RW(SDL_RWops *src, int freesrc) {
  assert(src->type == RW_TYPE_MEM);
  assert(freesrc == 0);
  return NULL;
}

SDL_Surface* IMG_Load(const char *filename) {
  
  FILE *file = fopen(filename, "r");
  // if (file == NULL) {
  //   printf("Cannot open file: %s\n", filename);
  //   return NULL;
  // }
  if (fseek(file, 0, SEEK_END) != 0) {
    fclose(file);
    return NULL;
  }
  size_t size = ftell(file);
  fseek(file, 0, SEEK_SET);
  char* buf = malloc(size);
  size_t read_size = fread(buf, 1, size, file);
  // if (fread(buf, size,1 , file) != size) {
  //   printf("Cannot read file: %s\n", filename);
  //   free(buf);
  //   fclose(file);
  //   return NULL;
  // }
  fclose(file);
  SDL_Surface* surface = STBIMG_LoadFromMemory(buf, size);
  free(buf);
  return surface;
}

int IMG_isPNG(SDL_RWops *src) {
  return 0;
}

SDL_Surface* IMG_LoadJPG_RW(SDL_RWops *src) {
  return IMG_Load_RW(src, 0);
}

char *IMG_GetError() {
  return "Navy does not support IMG_GetError()";
}
