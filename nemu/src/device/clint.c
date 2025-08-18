
#include <common.h>
#include <device/soc.h>

#define CLINT_SIZE 0x02000000

const uint32_t mtime       =  CLINT_SIZE + 0XBFF8;
const uint32_t mtime_high  =  CLINT_SIZE + 0XBFFC;

word_t clint_read(paddr_t addr, int len){
    uint64_t t = read_mtime();
    word_t result; 
    switch (addr){
        case mtime:
            result = (word_t)t; break;
        case mtime_high:
            result = (word_t)(t>>32);break;
        default:
            panic("clint_read: bad offset %x", addr);
    }
    return result;
}

uint64_t read_mtime(){
    return get_time();
}