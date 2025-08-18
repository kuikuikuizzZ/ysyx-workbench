
#include <common.h>
#include <device/map.h>
#include <device/mmio.h>

void uart_write(paddr_t addr, int len, word_t data){
    printf("uart write: %s",data);
    switch (len)
    {
    case 1:
        printf("%c",(uint8_t)data);break;
    case 2:
        printf("%c%c",(uint8_t)(data>>8),(uint8_t)(data));break;
    case 4:
        printf("%c",(uint8_t)(data>>24),(uint8_t)(data>>16),(uint8_t)(data>>8),(uint8_t)(data));break;  
    default:
        break;
    }
}