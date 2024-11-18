TOP_MODULE=top

all:
	verilator --cc --exe --build --top-module $(TOP_MODULE) -j 8  csrc/main.cpp  vsrc/*.v --trace

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by your self."

include ../Makefile

clean:
	rm -r obj_dir