BUILD_DIR = ./build

PRJ = npc

test:
	mill -i $(PRJ).test

build:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mkdir -p $(NPC_HOME)/svsrc
	mkdir -p $(NPC_HOME)/svsrc_no_soc
	mill -i $(PRJ).runMain Elaborate 
verilog: build
	sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(BUILD_DIR)/*.sv
	cp  -r $(wildcard $(BUILD_DIR)/*.sv $(BUILD_DIR)/*.v $(NPC_CHISEL_HOME)/npc/src/main/resources/*.v)  $(NPC_HOME)/svsrc/

verilog_old: build
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
	-rm -rf $(NPC_HOME)/svsrc

clean_old:
	-rm -rf $(BUILD_DIR)
	-rm -rf $(NPC_HOME)/svsrc_no_soc

.PHONY: test verilog help reformat checkformat clean build verilog_old

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by yourself."

-include ../Makefile
