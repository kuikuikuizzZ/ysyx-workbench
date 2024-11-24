#include <device/mmio.h>
#include <memory/memory.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif


    static uint8_t *pmem = NULL;

    void init_memory() {
        pmem = (uint8_t*)malloc(MSIZE);
    }

    uint8_t* guest_to_host(uint32_t paddr) { return pmem + paddr - MBASE; }

    void pmem_read(int raddr, int len, int *rword) {
        if (in_pmem(raddr)) {
            // TODO: support mask read
            *rword = host_read(guest_to_host(raddr),len);
            // printf("pmem read: raddr = %x, data %x len %d\n", raddr,*rword,len);
            return;
        }
        if (raddr==CONFIG_RTC_MMIO)
            *rword = mmio_read(raddr, len);
        return;
    }

    void pmem_write(int waddr,int len, int wdata){
        // printf("pmem write: waddr = %x data %x \n",waddr, wdata);
        if (in_pmem(waddr)){
            host_write(guest_to_host(waddr), len, wdata);
        }
        if (waddr==CONFIG_SERIAL_MMIO)
            mmio_write(waddr, len, wdata);
        return;
    }
#ifdef __cplusplus
}
#endif
