# Chisel专用配置
BUILD_DIR = $(BUILD_DIR_BASE)/npc-chisel
VERILATOR_FLAGS = $(VERILATOR_BASE_FLAGS) --Mdir $(BUILD_DIR)
TOP_NAME = ysyxSoCFull
NAME = V$(TOP_NAME)

# SV源文件
ifdef CONFIG_SOC
	SVSOURCES = $(wildcard $(NPC_HOME)/svsrc/*.v $(NPC_HOME)/svsrc/*.sv)
else
	SVSOURCES = $(wildcard $(NPC_HOME)/svsrc_no_soc/*.v $(NPC_HOME)/svsrc_no_soc/*.sv)
endif

BINARY = $(BUILD_DIR)/$(NAME)
NPC_EXEC = $(BINARY) $(ARGS) $(IMG)
NPC_PERF = $(BINARY) $(PERF_ARGS) $(IMG)


VSINC_PATH := $(SOC_HOME)/perip/uart16550/rtl/
VSINC_PATH += $(SOC_HOME)/perip/spi/rtl/
VINCLUDES = $(addprefix -I , $(VSINC_PATH))
VERILATOR_BASE_FLAGS += $(VINCLUDES)
VERILATOR_BASE_FLAGS += --top-module $(TOP_NAME)
IVERILOG_MAIN_FILE := $(NPC_HOME)/vsrc/iverilog_main.v

build:$(SVSOURCES) $(SOURCES) $(NVBOARD_ARCHIVE) 
	mkdir -p $(BUILD_DIR)
	verilator -Wno-DECLFILENAME  $(VERILATOR_FLAGS) $(SOURCES) $(SVSOURCES)  --trace-fst --autoflush

lint:$(SVSOURCES) $(SOURCES) $(NVBOARD_ARCHIVE) 
	verilator --lint-only -Wall -Wno-DECLFILENAME  $(VERILATOR_FLAGS) $(SOURCES) $(SVSOURCES) 

iverilog-build: $(SVSOURCES) $(IVERILOG_MAIN_FILE)
	mkdir -p $(BUILD_DIR)/iverilog
	iverilog $(VINCLUDES) -o $(BUILD_DIR)/iverilog/main.vvp  $(IVERILOG_MAIN_FILE) $(SVSOURCES) -g2012 

