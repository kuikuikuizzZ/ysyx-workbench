#ifdef __cplusplus
extern "C" {
#endif
    #include <memory.h>
    #include <stdlib.h>
    #include <string.h>


    static uint8_t *pmem = NULL;

    void init_memory() {
        pmem = (uint8_t*)malloc(MSIZE);
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
