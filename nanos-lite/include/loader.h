#ifndef __LOADER_H__
#define __LOADER_H__

#include <common.h>
#include <proc.h>

void naive_uload(PCB *pcb, const char *filename);
Context* context_uload (PCB *p, const char *filename);
#endif