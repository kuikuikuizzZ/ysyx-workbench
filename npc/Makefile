WORK_DIR  = $(shell pwd)
BUILD_DIR = $(WORK_DIR)/build
TOP_NAME = ysyx_24100012_top 
NAME=V$(TOP_NAME)
INC_PATH := $(WORK_DIR)/include $(INC_PATH)
INCLUDES = $(addprefix -I, $(INC_PATH))
VCFLAGS := $(INCLUDES) $(CFLAGS)
VERILATOR_FLAGS := --cc --exe --build -j 8 $(VERILATOR_FLAGS)
VERILATOR_FLAGS += --top-module $(TOP_NAME)
VERILATOR_FLAGS += --Mdir $(BUILD_DIR)
VERILATOR_FLAGS += -CFLAGS $(VCFLAGS)
VERILATOR_FLAGS += $(INCLUDES)

# include $(BUILD_DIR)/*.mk


BINARY   = $(BUILD_DIR)/$(NAME)
IMG ?=
NPC_EXEC := $(BINARY) $(ARGS) $(IMG)

build:
	verilator  $(VERILATOR_FLAGS) csrc/main.cpp csrc/memory.c vsrc/*.v --trace 

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by your self."

include ../Makefile



run: build
	$(NPC_EXEC)

clean:
	rm -r $(BUILD_DIR)

.PHONY: run build clean