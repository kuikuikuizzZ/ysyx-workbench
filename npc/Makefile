# Include all filelist.mk to merge file lists
FILELIST_MK = $(shell find -L ./csrc -name "filelist.mk")
include $(FILELIST_MK)

WORK_DIR  = $(shell pwd)
BUILD_DIR = $(WORK_DIR)/build
TOP_NAME = ysyx_24100012_top 
NAME=V$(TOP_NAME)
INC_PATH := $(WORK_DIR)/include $(INC_PATH)
INCLUDES = $(addprefix -CFLAGS -I, $(INC_PATH))
VCFLAGS :=  $(CFLAGS) $(INCLUDES)
LIBS += -lreadline
LDFLAGS +=   $(LIBS)


VERILATOR_FLAGS := --cc --exe --build -j 8 --vpi $(VERILATOR_FLAGS)
VERILATOR_FLAGS +=  $(VCFLAGS) 
VERILATOR_FLAGS += -LDFLAGS $(LDFLAGS)
VERILATOR_FLAGS += --top-module $(TOP_NAME)
VERILATOR_FLAGS += --Mdir $(BUILD_DIR)


SRCS=$(wildcard csrc/**/*.c csrc/*.c )
SOURCES = $(filter-out $(SRCS-BLACKLIST-y),$(SRCS))
VSOURCES=$(wildcard vsrc/**/*.v vsrc/*.v)
BINARY   = $(BUILD_DIR)/$(NAME)
IMG ?=
NPC_EXEC := $(BINARY) $(ARGS) $(IMG)

build:
	verilator  $(VERILATOR_FLAGS) csrc/main.cpp $(SOURCES) $(VSOURCES) --trace 

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by your self."

include ../Makefile



run: build
	$(NPC_EXEC)

clean:
	rm -r $(BUILD_DIR)

.PHONY: run build clean