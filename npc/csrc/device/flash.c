#include <stdio.h>
#include <common.h>
#include <device/device.h>
#include <memory/memory.h>

static uint8_t npc_flash[CONFIG_FLASH_SIZE] = {0};

unsigned short _mem[] = {
	0x0, 0x0258, 0x4abc, 0x7fff, 0x8000, 0x8100, 0xabcd, 0xffff
};

void init_flash() {
    // npc_flash = (uint8_t*)malloc(CONFIG_FLASH_SIZE);
    memcpy(npc_flash, _mem, sizeof(_mem));
}

#ifdef __cplusplus
extern "C" {
#endif
#ifdef CONFIG_HAS_FLASH

    uint8_t* guest_to_flash(uint32_t paddr) { return npc_flash + paddr; }

    void flash_read(int32_t addr, int32_t *data){
        // if (addr >= CONFIG_FLASH_BASE && 
        // addr < CONFIG_FLASH_BASE + CONFIG_FLASH_SIZE)
        *data = host_read(guest_to_flash(addr), 4);
        printf("flash_read: address %x data %x \n", addr,*data);
            // *data = 0x00100073;
        // else 
        //     printf("flash_read: invalid address %x\n", addr);
    }
#endif

#ifdef __cplusplus
}
#endif
