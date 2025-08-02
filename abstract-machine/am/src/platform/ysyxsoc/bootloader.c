#include <am.h>
#include <klib-macros.h>
#include <ysyxsoc.h>
#include <riscv.h>

extern char _heap_start;
extern char _data_start;

extern char _lma_code_start,_text_start,_erodata;
extern char _lma_data_start,_data_start, _data_end,_bss_start, _bss_end;
extern char _lma_sec_boost_start,_sec_boost_start, _sec_boost_end;
extern char _trm_init;
void init_uart();

__attribute__ ((section(".first_boost"))) void _load_sec_boost() {
    char *src = &_lma_sec_boost_start;
    char *dst = &_sec_boost_start;
    while (dst < &_sec_boost_end) {
        *dst++ = *src++;
    }
    void (*sec_boost)(void) = (void(*)(void))&_sec_boost_start;
    sec_boost();
}

__attribute__ ((section(".sec_boost"))) void _sec_boost(){
    putch('s');
    putch('e');
    putch('c');
    putch('\n');
    init_uart();

    // load code from LMA to VMA
    char *src = &_lma_code_start;
    char *dst = &_text_start;
    while (dst < &_erodata) {
        *dst++ = *src++;
    }

    // load data from LMA to VMA
    src = &_lma_data_start;
    dst = &_data_start;
    while (dst < &_data_end) {
        *dst++ = *src++;
    }
    // init empty data
    for (dst = &_bss_start; dst < &_bss_end; dst++)
        *dst = 0;
    
    void (*app_entry)(void) = (void(*)(void))&_trm_init;
    app_entry();
}