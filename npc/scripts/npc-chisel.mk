# Chisel专用配置
BUILD_DIR = $(BUILD_DIR_BASE)/npc-chisel
VERILATOR_FLAGS = $(VERILATOR_BASE_FLAGS) --Mdir $(BUILD_DIR)
TOP_NAME = Top
NAME = V$(TOP_NAME)

# SV源文件
SVSOURCES = $(wildcard $(NPC_HOME)/svsrc/*.v $(NPC_HOME)/svsrc/*.sv)
BINARY = $(BUILD_DIR)/$(NAME)
NPC_EXEC = $(BINARY) $(ARGS) $(IMG)

# Verilator基础配置
VERILATOR_BASE_FLAGS := --cc --exe --build -j 8 --vpi
VERILATOR_BASE_FLAGS += $(VCFLAGS)
VERILATOR_BASE_FLAGS += $(LDFLAGS)
VERILATOR_BASE_FLAGS += --top-module $(TOP_NAME)

build:
	mkdir -p $(BUILD_DIR)
	verilator $(VERILATOR_FLAGS) $(NPC_HOME)/csrc/main.cpp $(SOURCES) $(SVSOURCES) --trace