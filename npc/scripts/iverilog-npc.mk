# Chisel 生成 verilog 配置
CHISEL_IVERILOG_CONFIG=ICACHE_SIZE_BITS=3
CHISEL_IVERILOG_CONFIG+=ICACHE_BLOCK_BITS=0
CHISEL_IVERILOG_CONFIG+=ICACHE_ENABLE_BURST=false
CHISEL_IVERILOG_CONFIG+=ENABLE_SOC=false
CHISEL_IVERILOG_CONFIG+=NPC_ENABLE_DEBUG=false
CHISEL_IVERILOG_CONFIG+=NPC_ENABLE_IVERILOG=true

IVERILOG_MAIN_FILE := $(NPC_HOME)/vsrc/iverilog_main.v

verilog-iverilog: 
	@echo CHISEL_IVERILOG_CONFIG $(CHISEL_IVERILOG_CONFIG) 
	$(MAKE) -C $(NPC_CHISEL_HOME) $(CHISEL_IVERILOG_CONFIG) verilog-iverilog

iverilog-build: $(SVSOURCES) $(IVERILOG_MAIN_FILE)
	mkdir -p $(BUILD_DIR)/iverilog
	iverilog $(VINCLUDES) -o $(BUILD_DIR)/iverilog/main.vvp  $(IVERILOG_MAIN_FILE) $(SVSOURCES) -g2012 

sim-iverilog: iverilog-build
	@echo $(ARGS) $(IMG)
# 	@python $(NPC_HOME)/iverilog_scripts/bin2hex.py $(IMG) $(IMG).hex
	vvp $(BUILD_DIR)/iverilog/main.vvp  
