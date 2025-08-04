#include <stdio.h>
#include <common.h>
#include <device/device.h>
#include <memory/memory.h>
#define SRAM_BIT_LENGTH 2
static uint16_t npc_sdram[CONFIG_SDRAM_BA_BITS][CONFIG_SDRAM_ROW_BITS][CONFIG_SDRAM_COL_BITS][SRAM_BIT_LENGTH] = {0};

void init_sdram() {
    return;
}

const uint16_t masks[4] = {0xffff,0xff00,0x00ff,0x0000};

#ifdef __cplusplus
extern "C" {
#endif
#ifdef CONFIG_HAS_SDRAM
    void sdram_read(int32_t bit, int32_t row,int32_t col,int32_t ba,int32_t dqm, uint32_t *data){
        uint16_t ret = npc_sdram[ba][row][col][bit];
        uint16_t mask = masks[dqm];
        *data = (ret & mask);
        // printf("read sdram [ba][row][col] %x %x %x data %x dqm %d \n", ba,row,col,*data,dqm );
    }

    void sdram_write(int32_t bit, int32_t row,int32_t col,int32_t ba,int32_t dqm, uint32_t data){
        // printf("sdram_write: address %x x length %d, data %x \n", addr,length,data);
        uint16_t ret = npc_sdram[ba][row][col][bit];
        uint16_t mask = masks[dqm];
        npc_sdram[ba][row][col][bit] = (ret & ~mask) | (data & mask);
        // uint32_t new_data =  host_read(guest_to_sdram(addr), 4);
        // printf("write [ba][row][col] %x %x %x data %x dqm %d mask %08x \n", ba,row,col,npc_sdram[ba][row][col][bit],dqm,mask );

    }
#endif

#ifdef __cplusplus
}
#endif