BUILD_DIR = ./build

PRJ = npc

test:
	mill -i $(PRJ).test

ifeq ($(ARCH), riscv32e-npc)
	export ICacheSizeBits=3
	export ICacheBlockBits=0
	export ICacheEnableBurst=false
else 
# ifeq ($(ARCH), riscv32e-ysyxsoc)
	export ICACHE_SIZE_BITS=3
	$(ICACHE_BLOCK_BITS)=1
	$(ICACHE_ENABLE_BURST)=true
endif

verilog_build:
	$(call git_commit, "generate verilog")
	@echo ICACHE_SIZE_BITS $(ICACHE_SIZE_BITS)
	mkdir -p $(BUILD_DIR)
	mkdir -p $(NPC_HOME)/build
	mkdir -p $(NPC_HOME)/svsrc_no_soc
	mill -i $(PRJ).runMain Elaborate 

verilog_npc_build:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mkdir -p $(NPC_HOME)/build
	mkdir -p $(NPC_HOME)/svsrc_no_soc
	mill -i $(PRJ).runMain ElaborateNPC 


verilog: verilog_build
	sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(BUILD_DIR)/*.v
	sed -i '/\.v/ s/.*//' $(BUILD_DIR)/*.v
	cp  -r $(wildcard $(BUILD_DIR)/*.sv $(BUILD_DIR)/*.v)  $(NPC_HOME)/build/


verilog_npc: verilog_npc_build
# 	gsed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(BUILD_DIR)/*.sv
	cp  -r $(wildcard $(BUILD_DIR)/*.sv $(BUILD_DIR)/*.v $(NPC_CHISEL_HOME)/npc/src/main/resources/*.v)  $(NPC_HOME)/svsrc_no_soc/


help:
	mill -i $(PRJ).runMain Elaborate --help

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.idea.GenIdea/idea

clean:
	-rm -rf $(BUILD_DIR)
	-rm -rf $(NPC_HOME)/build

clean_old:
	-rm -rf $(BUILD_DIR)
	-rm -rf $(NPC_HOME)/svsrc_no_soc

.PHONY: test verilog help reformat checkformat clean build verilog_old

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by yourself."

-include ../Makefile
