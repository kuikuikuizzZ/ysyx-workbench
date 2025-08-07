# 通用配置和变量定义
WORK_DIR  = $(shell pwd)
BUILD_DIR_BASE = $(WORK_DIR)/build

# 包含配置文件(包括 menuconfig生成的配置)

include $(NPC_HOME)/.config
include $(NPC_HOME)/scripts/config.mk
include $(NPC_HOME)/tools/difftest.mk

GUEST_ISA ?= riscv32
FILELIST_MK = $(shell find -L ./csrc -name "filelist.mk")
include $(FILELIST_MK)

$(info FILELIST_MK = $(FILELIST_MK))
# 参数设置
override ARGS ?= --log=$(BUILD_DIR)/npc-log.txt
override ARGS += $(ARGS_DIFF)

# 编译器和链接器设置
# CFLAGS+= -g
CFLAGS+= -O3
INC_PATH := $(WORK_DIR)/include $(INC_PATH) 
INCLUDES = $(addprefix -CFLAGS -I, $(INC_PATH))
VCFLAGS := -CFLAGS $(CFLAGS) $(INCLUDES) 
LIBS += -lreadline
LDFLAGS += $(addprefix -LDFLAGS , $(LIBS))

# 源文件定义
DIRS-BLACKLIST-y += $(DIRS-BLACKLIST)
SRCS-BLACKLIST-y += $(SRCS-BLACKLIST) $(shell find -L $(DIRS-BLACKLIST-y) -name "*.c")
SRCS-y += $(shell find -L $(DIRS-y) -name "*.c")
SRCS = $(filter-out $(SRCS-BLACKLIST-y), $(SRCS-y))
SOURCES += $(addprefix ${NPC_HOME}/, $(SRCS))

$(info "SOURCES: $(SOURCES)")
$(info "ARGS: $(ARGS)")

# Verilator基础配置
VERILATOR_BASE_FLAGS := --cc --exe --build -j 8 --vpi --timescale "1ns/1ns" --no-timing --autoflush
VERILATOR_BASE_FLAGS += $(VCFLAGS)
VERILATOR_BASE_FLAGS += $(LDFLAGS)
VERILATOR_BASE_FLAGS += $(VINCLUDES)

ifdef CONFIG_WAVETRACE_FST
	VERILATOR_BASE_FLAGS += --trace-fst
endif

# 根据CHISEL标志选择编译路径
ifdef CONFIG_NPC_CHISEL
    include $(NPC_HOME)/scripts/npc-chisel.mk
else
    include $(NPC_HOME)/scripts/npc.mk
endif

ifdef CONFIG_NVBORAD
    include $(NVBOARD_HOME)/scripts/nvboard.mk
endif

# 通用规则
run: build
	@echo $(ARGS) $(IMG)
	$(NPC_EXEC)

clean:
	rm -rf $(BUILD_DIR_BASE)

sim:
	$(call git_commit, "sim RTL")
	@echo "Write this Makefile by your self."

.PHONY: run build clean sim

# 包含上级Makefile（保持原有依赖）
include ../Makefile
