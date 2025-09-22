# Chisel 生成 verilog 配置
CHISEL_IVERILOG_CONFIG=ICACHE_SIZE_BITS=2
CHISEL_IVERILOG_CONFIG+=ICACHE_BLOCK_BITS=0
CHISEL_IVERILOG_CONFIG+=ICACHE_ENABLE_BURST=false
CHISEL_IVERILOG_CONFIG+=ENABLE_SOC=false
CHISEL_IVERILOG_CONFIG+=NPC_ENABLE_DEBUG=false
CHISEL_IVERILOG_CONFIG+=NPC_ENABLE_IVERILOG=true

IVERILOG_MAIN_FILE := $(NPC_HOME)/iverilog_scripts/iverilog_main.v
NETLIST_MAIN_FILE := $(NPC_HOME)/iverilog_scripts/iverilog_netlist_main.v

NETLIST_FILES := $(shell find $(NPC_HOME)/netlist -name "*.v")

verilog-iverilog: 
	@echo CHISEL_IVERILOG_CONFIG $(CHISEL_IVERILOG_CONFIG) 
	$(MAKE) -C $(NPC_CHISEL_HOME) $(CHISEL_IVERILOG_CONFIG) verilog-iverilog

verilog-netlist:
	@echo CHISEL_IVERILOG_CONFIG $(CHISEL_IVERILOG_CONFIG) 
	$(MAKE) -C $(NPC_CHISEL_HOME) $(CHISEL_IVERILOG_CONFIG) verilog-netlist

iverilog-config:
	$(MAKE) riscv32e-iverilog_defconfig

iverilog-build:  $(SVSOURCES) $(IVERILOG_MAIN_FILE) iverilog-config
	mkdir -p $(BUILD_DIR)/iverilog
	iverilog $(VINCLUDES) -o $(BUILD_DIR)/iverilog/main.vvp  $(IVERILOG_MAIN_FILE) $(SVSOURCES) -g2012 

netlist-build: $(NETLIST_FILES) $(NETLIST_MAIN_FILE) iverilog-config
	sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(NPC_HOME)/netlist/*.v
	mkdir -p $(BUILD_DIR)/netlist
	iverilog $(VINCLUDES) -o $(BUILD_DIR)/netlist/main.vvp  $(NETLIST_MAIN_FILE) $(NETLIST_FILES) -g2012 

sim-iverilog: iverilog-build
	@echo $(ARGS) $(IMG)
	@python $(NPC_HOME)/iverilog_scripts/bin2hex.py $(IMG) $(IMG).hex
	vvp $(BUILD_DIR)/iverilog/main.vvp  +image=$(IMG).hex
# 	vvp $(BUILD_DIR)/iverilog/main.vvp  

sim-iverilog-raw: iverilog-build
	vvp $(BUILD_DIR)/iverilog/main.vvp

sim-iverilog-netlist: netlist-build
	@python $(NPC_HOME)/iverilog_scripts/bin2hex.py $(IMG) $(IMG).hex
	vvp $(BUILD_DIR)/netlist/main.vvp  +image=$(IMG).hex 

sim-iverilog-netlist-raw: netlist-build
	vvp $(BUILD_DIR)/netlist/main.vvp  