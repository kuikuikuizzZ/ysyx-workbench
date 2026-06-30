#ifndef __CPU_SIMFRONTEND_H__
#define __CPU_SIMFRONTEND_H__

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

bool init_galois_sim_frontend(const char *trace_file);
void GaloisSimFrontFetch(int offset, int *valid, int *pc, int *instr, int *preDecode);
void GaloisSimFrontUpdatePtr(int updateCount);
void GaloisSimFrontRedirect(int redirectValid, int redirectPc, int redirectTarget, int redirectType);
void GaloisSimFrontRobCommit(int valid, int pc);

#ifdef __cplusplus
}
#endif

#endif
