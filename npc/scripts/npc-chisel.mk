# Chisel专用配置
ifdef CONFIG_IVERILOG
	BUILD_DIR = $(BUILD_DIR_BASE)/npc-chisel-iverilog
else
	ifdef CONFIG_SOC
		BUILD_DIR = $(BUILD_DIR_BASE)/npc-chisel-soc
	else 
		BUILD_DIR = $(BUILD_DIR_BASE)/npc-chisel
	endif
endif

ifdef CONFIG_SOC
	TOP_NAME = ysyxSoCFull
else 
	TOP_NAME = Top
endif

NAME = V$(TOP_NAME)


# SV源文件
ifdef CONFIG_IVERILOG
	SVSOURCES = $(wildcard $(NPC_HOME)/svsrc_iverilog/*.v $(NPC_HOME)/svsrc_iverilog/*.sv)
else 
	ifdef CONFIG_SOC
		SVSOURCES = $(wildcard $(NPC_HOME)/build/*.v $(NPC_HOME)/build/*.sv)
	else
		SVSOURCES = $(wildcard $(NPC_HOME)/build/Top*.v wildcard $(NPC_HOME)/build/Top*.sv $(NPC_HOME)/build/ysyx_24100012.v)
	endif
endif


BINARY = $(BUILD_DIR)/$(NAME)
NPC_EXEC = $(BINARY) $(ARGS) $(IMG)
NPC_PERF = $(BINARY) $(PERF_ARGS) $(IMG)

VSINC_PATH := $(SOC_HOME)/perip/uart16550/rtl
VSINC_PATH += $(SOC_HOME)/perip/spi/rtl
VINCLUDES = $(addprefix -I, $(VSINC_PATH))
VERILATOR_BASE_FLAGS += $(VINCLUDES)
VERILATOR_BASE_FLAGS += --top-module $(TOP_NAME)
VERILATOR_FLAGS = $(VERILATOR_BASE_FLAGS) --Mdir $(BUILD_DIR)

build: $(SVSOURCES) $(SOURCES) $(NVBOARD_ARCHIVE) 
	mkdir -p $(BUILD_DIR)
	verilator -Wno-DECLFILENAME  $(VERILATOR_FLAGS) $(SOURCES) $(SVSOURCES)  --trace-fst --autoflush

lint:$(SVSOURCES) $(SOURCES) $(NVBOARD_ARCHIVE) 
	verilator --lint-only -Wall -Wno-DECLFILENAME  $(VERILATOR_FLAGS) $(SOURCES) $(SVSOURCES) 

