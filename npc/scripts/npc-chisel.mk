# Chisel专用配置
BUILD_DIR = $(BUILD_DIR_BASE)/npc-chisel
VERILATOR_FLAGS = $(VERILATOR_BASE_FLAGS) --Mdir $(BUILD_DIR)
TOP_NAME = Top
NAME = V$(TOP_NAME)

# SV源文件
SVSOURCES = $(wildcard $(NPC_HOME)/svsrc/*.v $(NPC_HOME)/svsrc/*.sv)
BINARY = $(BUILD_DIR)/$(NAME)
NPC_EXEC = $(BINARY) $(ARGS) $(IMG)

VERILATOR_BASE_FLAGS += --top-module $(TOP_NAME)

build:
	mkdir -p $(BUILD_DIR)
	verilator $(VERILATOR_FLAGS) $(SOURCES) $(SVSOURCES) 