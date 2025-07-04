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
        // IFNDEF(CONFIG_DEVICE, *rword = mmio_read(raddr, len)); // if not device, return

        // bool ren = false;
        // IFDEF(CONFIG_HAS_VGA, ren = ren||(raddr>=CONFIG_VGA_CTL_MMIO && raddr< (CONFIG_VGA_CTL_MMIO+8)) || 
        // (raddr>=CONFIG_FB_ADDR && raddr< (CONFIG_FB_ADDR+ screen_size)));
        // // IFNDEF(CONFIG_HAS_I8042, ren = ren || (raddr==CONFIG_I8042_DATA_MMIO));
        // IFDEF(CONFIG_HAS_TIMER, ren = ren || (raddr==CONFIG_RTC_MMIO || raddr==(CONFIG_RTC_MMIO+4)));
        // IFDEF(CONFIG_HAS_SERIAL, ren = ren||(raddr==CONFIG_SERIAL_MMIO || raddr==(CONFIG_SERIAL_MMIO+4)));

        // if (ren) *rword = mmio_read(raddr, len);
        IFDEF(CONFIG_DEVICE, {
            if (raddr==CONFIG_RTC_MMIO ||  raddr==(CONFIG_RTC_MMIO+4) ||
            (raddr>=CONFIG_VGA_CTL_MMIO && raddr<(CONFIG_VGA_CTL_MMIO+8)) || 
            (raddr>=CONFIG_FB_ADDR && raddr< CONFIG_FB_ADDR+ screen_size) ||
            raddr==CONFIG_I8042_DATA_MMIO)
            *rword = mmio_read(raddr, len);
        });
        return;
    }

    void pmem_write(int waddr,int len, int wdata){
        // printf("pmem write: waddr = %x data %x \n",waddr, wdata);
        if (in_pmem(waddr)){
            host_write(guest_to_host(waddr), len, wdata);
        }
        // IFNDEF(CONFIG_DEVICE, mmio_write(waddr, len, wdata)); // if not device, return
        // bool wen = false;
        // IFDEF(CONFIG_HAS_VGA,wen = wen || (waddr>=CONFIG_VGA_CTL_MMIO && waddr<(CONFIG_VGA_CTL_MMIO+8)) || 
        // (waddr>=CONFIG_FB_ADDR && waddr< (CONFIG_FB_ADDR+ screen_size)));
        // // IFDEF(CONFIG_HAS_I8042,wen = wen || (waddr==CONFIG_I8042_DATA_MMIO));
        // IFDEF(CONFIG_HAS_TIMER,wen = wen || (waddr==CONFIG_RTC_MMIO || waddr==(CONFIG_RTC_MMIO+4)));
        // IFDEF(CONFIG_HAS_SERIAL,wen = wen || (waddr==CONFIG_SERIAL_MMIO || waddr==(CONFIG_SERIAL_MMIO+4)));
        // if (wen) ;
        IFDEF(CONFIG_DEVICE, {
        if (waddr==CONFIG_SERIAL_MMIO || waddr==(CONFIG_SERIAL_MMIO+4) ||
            (waddr>=CONFIG_VGA_CTL_MMIO && waddr<(CONFIG_VGA_CTL_MMIO+8)) || 
            (waddr>=CONFIG_FB_ADDR && waddr< (CONFIG_FB_ADDR+ screen_size)) ||
            waddr == CONFIG_I8042_DATA_MMIO)
            mmio_write(waddr, len, wdata);
        });
        return;
    }

#ifdef __cplusplus
}
#endif
