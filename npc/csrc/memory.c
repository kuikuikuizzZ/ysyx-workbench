#ifdef __cplusplus
extern "C" {
#endif
    #include <memory.h>
    #include <stdlib.h>
    #include <string.h>


    static uint8_t *pmem = NULL;
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


    void init_memory() {
    pmem = (uint8_t*)malloc(MSIZE);
    }

    void init_isa(){
    /* Load built-in image. */
    memcpy(guest_to_host(MBASE), img, sizeof(img));
    }



    uint8_t* guest_to_host(uint32_t paddr) { return pmem + paddr - MBASE; }


    void pmem_read(int raddr, int len, int *rword) {
        // printf("pmem read: raddr = %x\n", raddr);
        if (in_pmem(raddr)) {
            // TODO: support mask read
            *rword = host_read(guest_to_host(raddr),len);
            return;
        }
    }

    void pmem_write(int waddr,int len, int *wdata){
        if (in_pmem(waddr)){
            host_write(guest_to_host(waddr), len, *wdata);
        }
    }

#ifdef __cplusplus
}
#endif