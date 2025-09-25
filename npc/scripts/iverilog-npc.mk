# Chisel 生成 verilog 配置
CHISEL_IVERILOG_CONFIG=ICACHE_SIZE_BITS=2
CHISEL_IVERILOG_CONFIG+=ICACHE_BLOCK_BITS=0
CHISEL_IVERILOG_CONFIG+=ICACHE_ENABLE_BURST=false
CHISEL_IVERILOG_CONFIG+=ENABLE_SOC=false
CHISEL_IVERILOG_CONFIG+=NPC_ENABLE_DEBUG=false
CHISEL_IVERILOG_CONFIG+=NPC_ENABLE_IVERILOG=true

IVERILOG_MAIN_FILE := $(NPC_HOME)/iverilog_scripts/iverilog_main.v
NETLIST_MAIN_FILE := $(NPC_HOME)/iverilog_scripts/iverilog_netlist_main.v

NETLIST_FILES := $(wildcard $(NPC_HOME)/build/Iverilog*.v wildcard $(NPC_HOME)/build/Iverilog*.sv  $(NPC_HOME)/build/TopAXI4LiteSlave.sv $(NPC_HOME)/build/Top_mask_expander.v)

verilog-iverilog: 
	@echo CHISEL_IVERILOG_CONFIG $(CHISEL_IVERILOG_CONFIG) 
	$(MAKE) -C ../npc-chisel $(CHISEL_IVERILOG_CONFIG) verilog-iverilog

verilog-netlist:
	@echo CHISEL_IVERILOG_CONFIG $(CHISEL_IVERILOG_CONFIG) 
	$(MAKE) -C ../npc-chisel $(CHISEL_IVERILOG_CONFIG) verilog-netlist

iverilog-config:
	$(MAKE) -C $(NPC_HOME) riscv32e-iverilog_defconfig

iverilog-build:  $(SVSOURCES) $(IVERILOG_MAIN_FILE) 
	mkdir -p $(BUILD_DIR)/iverilog
	sed -i 's/pc_reg[[:space:]]*<=[[:space:]]*32'\''h[0-9a-fA-F]\{8\}/pc_reg <= 32'\''h${START_ADDR}/g' $(NPC_HOME)/build/ysyx_24100012.v
	
	iverilog $(VINCLUDES) -o $(BUILD_DIR)/iverilog/main.vvp  $(IVERILOG_MAIN_FILE) $(SVSOURCES) -g2012 

netlist-build: $(NETLIST_FILES) $(NETLIST_MAIN_FILE) 
	sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(NPC_HOME)/build/*.v
	mkdir -p $(BUILD_DIR)/netlist
	iverilog $(VINCLUDES) -o $(BUILD_DIR)/netlist/main.vvp  $(NETLIST_MAIN_FILE) $(NETLIST_FILES) $(NETLIST) $(CELLS) -g2012 

sim-iverilog: 
	@echo $(ARGS) $(IMG)
	$(MAKE) -C $(NPC_HOME) iverilog-config
	$(MAKE) -C $(NPC_HOME) iverilog-run IMG=$(IMG)

iverilog-run:iverilog-build
	@python $(NPC_HOME)/iverilog_scripts/bin2hex.py $(IMG) $(IMG).hex
	vvp $(BUILD_DIR)/iverilog/main.vvp  +image=$(IMG).hex

netlist-run:netlist-build
	@python $(NPC_HOME)/iverilog_scripts/bin2hex.py $(IMG) $(IMG).hex
	vvp $(BUILD_DIR)/netlist/main.vvp  +image=$(IMG).hex

sim-iverilog-raw: iverilog-build
	vvp $(BUILD_DIR)/iverilog/main.vvp

sim-iverilog-netlist: netlist-build
	$(MAKE) -C $(NPC_HOME) iverilog-config
	$(MAKE) -C $(NPC_HOME) netlist-run IMG=$(IMG) CELLS=$(CELLS) NETLIST=$(NETLIST)

sim-iverilog-netlist-raw: netlist-build
	vvp $(BUILD_DIR)/netlist/main.vvp  