#ifndef __LOADER_H__
#define __LOADER_H__

#include <common.h>
#include <proc.h>

void naive_uload(PCB *pcb, const char *filename);
Context* context_uload (PCB *p, const char *filename, char *const argv[], char *const envp[]) ;
Context* context_kload(PCB *p, void (*entry)(void *), void *arg);
#endif