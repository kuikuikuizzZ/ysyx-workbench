#include <am.h>
#include <ysyxsoc.h>
#include <riscv.h>
#include <klib.h>

// #define KEYDOWN_MASK 0x8000
#define KEYDOWN_MASK 0x000000ff

// void __am_input_keybrd(AM_INPUT_KEYBRD_T *kbd) {
//   uint32_t key=inl(KBD_ADDR);

//   if (key==AM_KEY_NONE){
//     kbd->keycode = AM_KEY_NONE;
//     kbd->keydown = 0;
//   } else{
//     kbd->keydown = (key&KEYDOWN_MASK)==KEYDOWN_MASK;
//     kbd->keycode = (kbd->keydown)?key-KEYDOWN_MASK:key;
//   }
// }


void __am_input_keybrd(AM_INPUT_KEYBRD_T *kbd) {
  uint32_t key=inb(KEYBOARD_BASE);

  if (key==AM_KEY_NONE){
    kbd->keycode = AM_KEY_NONE;
    kbd->keydown = 0;
  } else{
    kbd->keydown = (key&KEYDOWN_MASK)==KEYDOWN_MASK;
    kbd->keycode = (kbd->keydown)?key-KEYDOWN_MASK:key;
  }
}
