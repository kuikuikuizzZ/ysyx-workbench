#include <am.h>
#include <ysyxsoc.h>
#include <riscv.h>
#include <klib.h>

// #define KEYDOWN_MASK 0x8000
// #define KEYDOWN_MASK 0x000000ff
#define BREAK_CODE 0xF0
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
char scancode_to_ascii(uint8_t scan_byte);


void __am_input_keybrd(AM_INPUT_KEYBRD_T *kbd) {
    char code = inl(KEYBOARD_BASE);
    char key = 0;
    if (code == BREAK_CODE) {
        kbd->keydown = 0;
        code = inl(KEYBOARD_BASE);
    } else{
        kbd->keydown = 1;
    }     
    key = scancode_to_ascii(code);
    if (key == 0) {
        kbd->keycode = 0;
        // putch('-');
    }
    kbd->keycode = key;
}


static const char BASE_KEYMAP[128] = {
    [0x00] = 0,      [0x01] = 0,      [0x02] = 0,      [0x03] = 0,
    [0x04] = 0,      [0x05] = 0,      [0x06] = 0,      [0x07] = 0,
    [0x08] = 0,      [0x09] = 0,      [0x0A] = 0,      [0x0B] = 0,
    [0x0C] = 0,      [0x0D] = '\t',   [0x0E] = '`',    [0x0F] = 0,
    [0x10] = 0,      [0x11] = 0,      [0x12] = 0,      [0x13] = 0,
    [0x14] = 0,      [0x15] = 'q',    [0x16] = '1',    [0x17] = 0,
    [0x18] = 0,      [0x19] = 0,      [0x1A] = 'z',    [0x1B] = 's',
    [0x1C] = 'a',    [0x1D] = 'w',    [0x1E] = '2',    [0x1F] = 0,
    [0x20] = 0,      [0x21] = 'c',    [0x22] = 'x',    [0x23] = 'd',
    [0x24] = 'e',    [0x25] = '4',    [0x26] = '3',    [0x27] = 0,
    [0x28] = 0,      [0x29] = ' ',    [0x2A] = 'v',    [0x2B] = 'f',
    [0x2C] = 't',    [0x2D] = 'r',    [0x2E] = '5',    [0x2F] = 0,
    [0x30] = 0,      [0x31] = 'n',    [0x32] = 'b',    [0x33] = 'h',
    [0x34] = 'g',    [0x35] = 'y',    [0x36] = '6',    [0x37] = 0,
    [0x38] = 0,      [0x39] = 0,      [0x3A] = 'm',    [0x3B] = 'j',
    [0x3C] = 'u',    [0x3D] = '7',    [0x3E] = '8',    [0x3F] = 0,
    [0x40] = 0,      [0x41] = ',',    [0x42] = 'k',    [0x43] = 'i',
    [0x44] = 'o',    [0x45] = '0',    [0x46] = '9',    [0x47] = 0,
    [0x48] = 0,      [0x49] = '.',    [0x4A] = '/',    [0x4B] = 'l',
    [0x4C] = ';',    [0x4D] = 'p',    [0x4E] = '-',    [0x4F] = 0,
    [0x50] = 0,      [0x51] = 0,      [0x52] = '\'',   [0x53] = 0,
    [0x54] = '[',    [0x55] = '=',    [0x56] = 0,      [0x57] = 0,
    [0x58] = 0,      [0x59] = 0,      [0x5A] = '\n',   [0x5B] = ']',
    [0x5C] = 0,      [0x5D] = '\\',   [0x5E] = 0,      [0x5F] = 0,
    [0x60] = 0,      [0x61] = 0,      [0x62] = 0,      [0x63] = 0,
    [0x64] = 0,      [0x65] = 0,      [0x66] = '\b',   [0x67] = 0,
    [0x68] = 0,      [0x69] = '1',    [0x6A] = 0,      [0x6B] = '4',
    [0x6C] = '7',    [0x6D] = 0,      [0x6E] = 0,      [0x6F] = 0,
    [0x70] = '0',    [0x71] = '.',    [0x72] = '2',    [0x73] = '5',
    [0x74] = '6',    [0x75] = '8',    [0x76] = 0x1B,   [0x77] = 0,
    [0x78] = 0,      [0x79] = '+',    [0x7A] = '3',    [0x7B] = '-',
    [0x7C] = '*',    [0x7D] = '9',    [0x7E] = 0,      [0x7F] = 0
};


char scancode_to_ascii(uint8_t scancode) {
    // 0xF0是断码标志
    if (scancode == 0xF0) return 0xF0;
    
    // E0扩展序列标志 (单独字节不产生ASCII)
    if (scancode == 0xE0 || scancode == 0xE1) return 0;
    
    // 0x76是ESC键的特殊扫描码
    if (scancode == 0x76) return 0x1B;
    
    // 从映射表中获取ASCII值
    if (scancode < sizeof(BASE_KEYMAP)) {
        return BASE_KEYMAP[scancode];
    }
    
    return 0; // 未知扫描码返回0
}