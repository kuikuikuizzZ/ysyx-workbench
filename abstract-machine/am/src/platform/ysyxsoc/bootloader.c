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
    init_uart();
    putch('H');
    putch('i');
    putch('\n');

    // load code from LMA to VMA
    unsigned *src = (unsigned*)&_lma_code_start;
    unsigned *dst = (unsigned*)&_text_start;
    while (dst < (unsigned*)&_erodata)  *dst++ = *src++;
    char *csrc = (char*)src;
    char *cdst = (char*)dst;
    while (cdst < &_erodata ) *cdst++ = *csrc++;


    // load data from LMA to VMA
    src = (unsigned*)&_lma_data_start;
    dst = (unsigned*)&_data_start;
    while (dst < (unsigned*)&_data_end) *dst++ = *src++; 
    csrc = (char*)src;
    cdst = (char*)dst;
    while (cdst < &_data_end ) *cdst++ = *csrc++;
    
    // init empty data
    for (dst = (unsigned*)&_bss_start; dst < (unsigned*)&_bss_end; dst++)
        *dst = 0;
    cdst = (char*)dst;
    for (;cdst < &_bss_end; cdst++) *cdst=0;



    void (*app_entry)(void) = (void(*)(void))&_trm_init;
    app_entry();
}