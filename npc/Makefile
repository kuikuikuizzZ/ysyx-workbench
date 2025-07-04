# 通用配置和变量定义
WORK_DIR  = $(shell pwd)
BUILD_DIR_BASE = $(WORK_DIR)/build

# 包含配置文件(包括 menuconfig生成的配置)
FILELIST_MK = $(shell find -L ./csrc -name "filelist.mk")
include $(FILELIST_MK)
include $(NPC_HOME)/tools/difftest.mk
include $(NPC_HOME)/scripts/config.mk
include $(NPC_HOME)/.config

# 参数设置
override ARGS ?= --log=$(BUILD_DIR)/npc-log.txt
override ARGS += $(ARGS_DIFF)

# 编译器和链接器设置
# CFLAGS+= -g 
INC_PATH := $(WORK_DIR)/include $(INC_PATH)
INCLUDES = $(addprefix -CFLAGS -I, $(INC_PATH))
VCFLAGS := -CFLAGS $(CFLAGS) $(INCLUDES)
LIBS += -lreadline
LDFLAGS += $(addprefix -LDFLAGS , $(LIBS))



# 源文件定义
SRCS = $(wildcard $(NPC_HOME)/csrc/**/**/*.c $(NPC_HOME)/csrc/**/*.c $(NPC_HOME)/csrc/*.c)
SOURCES = $(filter-out $(SRCS-BLACKLIST-y), $(SRCS))

# 根据CHISEL标志选择编译路径
ifdef CONFIG_NPC_CHISEL
    include $(NPC_HOME)/scripts/npc-chisel.mk
else
    include $(NPC_HOME)/scripts/npc.mk
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
