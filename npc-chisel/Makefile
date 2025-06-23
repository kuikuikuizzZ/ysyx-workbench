BUILD_DIR = ./build

PRJ = npc

test:
	mill -i $(PRJ).test

build:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mkdir -p $(NPC_HOME)/svsrc
	mill -i $(PRJ).runMain Elaborate --split-verilog --target-dir $(BUILD_DIR) 

verilog: build
	cp  -r $(wildcard $(BUILD_DIR)/*.sv $(BUILD_DIR)/*.v) $(NPC_HOME)/svsrc/

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

.PHONY: test verilog help reformat checkformat clean build

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by yourself."

-include ../Makefile
