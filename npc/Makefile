all:
	verilator --cc --exe --build -j 0  csrc/main.cpp  vsrc/*.v --trace

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by your self."

include ../Makefile
