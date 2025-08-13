include $(AM_HOME)/scripts/isa/riscv.mk
include $(AM_HOME)/scripts/platform/ysyxsoc.mk
COMMON_CFLAGS += -march=rv32e_zicsr -mabi=ilp32e # overwrite
LDSCRIPTS += $(AM_HOME)/scripts/linker_ysyx_soc_psram.ld
LDFLAGS   += --defsym=_pmem_start=0xa1000000 --defsym=_entry_offset=0x00
LDFLAGS   += --gc-sections -e _start
LDFLAGS   += -melf32lriscv                     # overwrite
AM_SRCS += riscv/ysyxsoc/libgcc/div.S \
           riscv/ysyxsoc/libgcc/muldi3.S \
           riscv/ysyxsoc/libgcc/multi3.c \
           riscv/ysyxsoc/libgcc/ashldi3.c \
           riscv/ysyxsoc/libgcc/unused.c   \
           riscv/ysyxsoc/cte.c \
           riscv/ysyxsoc/start.S \
           riscv/ysyxsoc/trap.S \
           riscv/ysyxsoc/vme.c 