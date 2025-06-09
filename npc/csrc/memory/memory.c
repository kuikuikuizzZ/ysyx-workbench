#include <device/mmio.h>
#include <memory/memory.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
uint32_t screen_size = 300*400*sizeof(uint32_t);

#ifdef __cplusplus
extern "C" {
#endif


    static uint8_t *npc_pmem = NULL;

    void init_memory() {
        npc_pmem = (uint8_t*)malloc(MSIZE);
    }

    uint8_t* guest_to_host(uint32_t paddr) { return npc_pmem + paddr - MBASE; }

    void pmem_read(int raddr, int len, int *rword) {
        // printf("pmem read: raddr = %x, data %x len %d\n", raddr,*rword,len);
        if (in_pmem(raddr)) {
            // TODO: support mask read
            *rword = host_read(guest_to_host(raddr),len);
            return;
        }
        if (raddr==CONFIG_RTC_MMIO ||  raddr==(CONFIG_RTC_MMIO+4) ||
            (raddr>=CONFIG_VGA_CTL_MMIO && raddr<(CONFIG_VGA_CTL_MMIO+8)) || 
            (raddr>=CONFIG_FB_ADDR && raddr< CONFIG_FB_ADDR+ screen_size) ||
            raddr==CONFIG_I8042_DATA_MMIO)
            *rword = mmio_read(raddr, len);
        return;
    }

    void pmem_write(int waddr,int len, int wdata){
        // printf("pmem write: waddr = %x data %x \n",waddr, wdata);
        if (in_pmem(waddr)){
            host_write(guest_to_host(waddr), len, wdata);
        }
        if (waddr==CONFIG_SERIAL_MMIO || waddr==(CONFIG_SERIAL_MMIO+4) ||
            (waddr>=CONFIG_VGA_CTL_MMIO && waddr<(CONFIG_VGA_CTL_MMIO+8)) || 
            (waddr>=CONFIG_FB_ADDR && waddr< (CONFIG_FB_ADDR+ screen_size)) ||
            waddr == CONFIG_I8042_DATA_MMIO)
            
            mmio_write(waddr, len, wdata);
        return;
    }
#ifdef __cplusplus
}
#endif
