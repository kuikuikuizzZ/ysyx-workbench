WORK_DIR  = $(shell pwd)
BUILD_DIR = $(WORK_DIR)/build
BINARY   = $(BUILD_DIR)/$(NAME)
NAME = ysyx_24100012_top 
IMG ?=
NPC_EXEC := $(BINARY) $(ARGS) $(IMG)

build:
	verilator --cc --exe --build --Mdir $(BUILD_DIR) --top-module $(NAME) -j 8  csrc/main.cpp  vsrc/*.v --trace 

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by your self."

include ../Makefile

run: build
	$(call git_commit, "run NPC")
	$(NPC_EXEC)

clean:
	rm -r obj_dir

.PHONY: run build clean