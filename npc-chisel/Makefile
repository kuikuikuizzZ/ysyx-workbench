BUILD_DIR = ./build/soc
NPC_BUILD_DIR = ./build/npc
IVERILOG_BUILD_DIR = ./build/iverilog
PRJ = npc

test:
	mill -i $(PRJ).test

verilog_build:
	$(call git_commit, "generate verilog")
	@echo ICACHE_SIZE_BITS $(ICACHE_SIZE_BITS)
	mkdir -p $(BUILD_DIR)
	mkdir -p $(NPC_HOME)/build/soc
	mill -i $(PRJ).runMain Elaborate 

verilog_npc_build:
	$(call git_commit, "generate verilog")
	mkdir -p $(NPC_BUILD_DIR)
	mkdir -p $(NPC_HOME)/svsrc_no_soc
	mill -i $(PRJ).runMain ElaborateNPC 

verilog_iverilog_build:
	$(call git_commit, "generate verilog")
	mkdir -p $(IVERILOG_BUILD_DIR)
	mkdir -p $(NPC_HOME)/svsrc_iverilog
	mill -i $(PRJ).runMain ElaborateNPC 


verilog: verilog_build
	sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(BUILD_DIR)/*.v
	sed -i '/\.v/ s/.*//' $(BUILD_DIR)/*.v
	cp  -r $(wildcard $(BUILD_DIR)/*.sv $(BUILD_DIR)/*.v)  $(NPC_HOME)/build/


verilog-npc: verilog_npc_build
# 	gsed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(BUILD_DIR)/*.sv
	sed -i '/\.v/ s/.*//' $(NPC_BUILD_DIR)/npc/*.v
	cp  -r $(wildcard $(NPC_BUILD_DIR)/*.sv $(NPC_BUILD_DIR)/*.v $(NPC_CHISEL_HOME)/npc/src/main/resources/*.v)  $(NPC_HOME)/svsrc_no_soc/

verilog-iverlog: verilog_npc_build
# 	gsed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(BUILD_DIR)/*.sv
	sed -i '/\.v/ s/.*//' $(IVERILOG_BUILD_DIR)/*.v
	cp  -r $(wildcard $(IVERILOG_BUILD_DIR)/*.sv $(IVERILOG_BUILD_DIR)/*.v $(NPC_CHISEL_HOME)/npc/src/main/resources/*.v)  $(NPC_HOME)/svsrc_iverilog/

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

clean_npc:
	-rm -rf $(NPC_BUILD_DIR)
	-rm -rf $(NPC_HOME)/svsrc_no_soc

.PHONY: test verilog help reformat checkformat clean build verilog_old

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by yourself."

-include ../Makefile
