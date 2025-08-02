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

    uint8_t* guest_to_host(uint32_t paddr) { 
        // uint8_t *addr = npc_pmem + paddr - MBASE;
        // IFNDEF(CONFIG_IMEM_FLASH_BASE,addr =  npc_pmem + paddr - MBASE);
        // IFDEF(CONFIG_IMEM_FLASH_BASE, addr =  npc_pmem + paddr );
        return npc_pmem + paddr-MBASE;
    }
    uint8_t* guest_to_flash(uint32_t paddr) { return npc_pmem + paddr; }


    void pmem_read(int raddr, int len, int *rword) {
        // printf("pmem read: raddr = %x, data %x len %d\n", raddr,*rword,len);
        if (in_pmem(raddr)) {
            // TODO: support mask read
            *rword = host_read(guest_to_host(raddr),len);
            return;
        }
        IFDEF(CONFIG_DEVICE, {
            // if (raddr==CONFIG_RTC_MMIO ||  raddr==(CONFIG_RTC_MMIO+4) ||
            // (raddr>=CONFIG_VGA_CTL_MMIO && raddr<(CONFIG_VGA_CTL_MMIO+8)) || 
            // (raddr>=CONFIG_FB_ADDR && raddr< CONFIG_FB_ADDR+ screen_size) ||
            // raddr==CONFIG_I8042_DATA_MMIO)
            if (raddr==CONFIG_RTC_MMIO ||  raddr==(CONFIG_RTC_MMIO+4) )
            *rword = mmio_read(raddr, len);
        });
        return;
    }

    void pmem_write(int waddr,int len, int wdata){
        // printf("pmem write: waddr = %x data %x \n",waddr, wdata);
        if (in_pmem(waddr)){
            host_write(guest_to_host(waddr), len, wdata);
        }
        IFDEF(CONFIG_DEVICE, {
        // if (waddr==CONFIG_SERIAL_MMIO || waddr==(CONFIG_SERIAL_MMIO+4) ||
        //     (waddr>=CONFIG_VGA_CTL_MMIO && waddr<(CONFIG_VGA_CTL_MMIO+8)) || 
        //     (waddr>=CONFIG_FB_ADDR && waddr< (CONFIG_FB_ADDR+ screen_size)) ||
        //     waddr == CONFIG_I8042_DATA_MMIO)
        if (waddr==CONFIG_RTC_MMIO || waddr==(CONFIG_RTC_MMIO+4) || 
            (waddr==CONFIG_SERIAL_MMIO) ||  (waddr==CONFIG_SERIAL_MMIO+4))
            mmio_write(waddr, len, wdata);
        });
        return;
    }



    void pmem_mask_read(int raddr, int mask, int *rword) {
        if (in_pmem(raddr)) {
            // TODO: support mask read, 4 bytes read and npc take care of mask
            *rword = host_read(guest_to_host(raddr),4);
            printf("pmem read: raddr = %x, data %.8x mask %.8x\n", raddr,*rword,mask);
            return;
        }
        IFDEF(CONFIG_DEVICE, {
            // if (raddr==CONFIG_RTC_MMIO ||  raddr==(CONFIG_RTC_MMIO+4) ||
            // (raddr>=CONFIG_VGA_CTL_MMIO && raddr<(CONFIG_VGA_CTL_MMIO+8)) || 
            // (raddr>=CONFIG_FB_ADDR && raddr< CONFIG_FB_ADDR+ screen_size) ||
            // raddr==CONFIG_I8042_DATA_MMIO)
            if (raddr==CONFIG_RTC_MMIO ||  raddr==(CONFIG_RTC_MMIO+4) )
            // TODO: support mask read, 4 bytes read and npc take care of mask
            *rword = mmio_read(raddr, 4);
        });
        return;
    }

    void pmem_mask_write(int waddr,int mask, int wdata){
        if (in_pmem(waddr)){
            // align write
            uint32_t rword = host_read(guest_to_host(waddr),4);
            rword = (rword & ~mask) | (wdata & mask);
            host_write(guest_to_host(waddr), 4,rword);
            printf("pmem write: waddr = %x data %.8x , mask %.8x \n",waddr, wdata, mask);
        }
        IFDEF(CONFIG_DEVICE, {
        // if (waddr==CONFIG_SERIAL_MMIO || waddr==(CONFIG_SERIAL_MMIO+4) ||
        //     (waddr>=CONFIG_VGA_CTL_MMIO && waddr<(CONFIG_VGA_CTL_MMIO+8)) || 
        //     (waddr>=CONFIG_FB_ADDR && waddr< (CONFIG_FB_ADDR+ screen_size)) ||
        //     waddr == CONFIG_I8042_DATA_MMIO)
        if (waddr==CONFIG_RTC_MMIO || waddr==(CONFIG_RTC_MMIO+4) || 
            (waddr==CONFIG_SERIAL_MMIO) ||  (waddr==CONFIG_SERIAL_MMIO+4))
            // align write
            mmio_write(waddr, 4, wdata&mask);
        });

        return;
    }

#ifdef __cplusplus
}
#endif
