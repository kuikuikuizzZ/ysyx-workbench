#ifndef __ISA_H__
#define __ISA_H__

#ifdef __cplusplus
extern "C" {
#endif


    #include <memory.h>
    #include <string.h>
    #include <common.h>

    #define RV_NR 16

    typedef struct {
        word_t gpr[RV_NR];
        vaddr_t pc; 
    } CPU_state;

    // this is not consistent with uint8_t
    // but it is ok since we do not access the array directly
    static const uint32_t img [] = {
            0x00100513,      //addi a0 x0 1
            0x00150513,      //addi a0 a0 1
            0x00150513,      //addi a0 a0 1
            0x00150513,      //addi a0 a0 1
            0x00150513,      //addi a0 a0 1
            0x00150513,      //addi a0 a0 1
            0x00108073,      //ebreak
    };
    void init_isa(){
    /* Load built-in image. */
        memcpy(guest_to_host(MBASE), img, sizeof(img));
    }

#ifdef __cplusplus
}
#endif
#endif