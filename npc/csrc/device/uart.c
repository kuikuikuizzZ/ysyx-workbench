#include <stdio.h>
#include <common.h>
#include <device/device.h>
#include <memory/memory.h>


#ifdef CONFIG_HAS_UART
    bool in_uart(uint32_t paddr) { 
        return paddr >= CONFIG_UART_BASE && 
            paddr < CONFIG_UART_BASE + CONFIG_UART_SIZE; 
    }
    
#endif