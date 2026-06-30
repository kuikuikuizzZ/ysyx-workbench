#include "simfrontend.h"

#include "ftq.h"
#include "tracereader.h"

#include <cstdio>
#include <cstdlib>
#include <string>
#include <string_view>

static MmapLineReader trace_fetch;
static ContinuityChecker pc_checker;
static FetchTargetQueue ftq;
static bool initialized = false;

static void require_initialized() {
  if (!initialized) {
    std::fprintf(stderr, "simfrontend: not initialized. Run npc with --simfront-trace <trace-file>.\n");
    std::abort();
  }
}

static bool parse_probe_line(uint32_t &pc, uint32_t &instr) {
  std::string_view next_line;
  if (!trace_fetch.probe_next_line(next_line)) {
    return false;
  }
  return parse_line(next_line, pc, instr);
}

static bool fetch_next_trace_line() {
  std::string_view line;
  while (trace_fetch.get_next_line(line)) {
    uint32_t pc = 0;
    uint32_t instr = 0;
    if (!parse_line(line, pc, instr)) {
      continue;
    }

    uint32_t next_pc = pc + (is_rvc(instr) ? 2 : 4);
    uint32_t next_instr = 0x00004033;
    uint32_t probed_pc = 0;
    uint32_t probed_instr = 0;
    if (parse_probe_line(probed_pc, probed_instr)) {
      next_pc = probed_pc;
      next_instr = probed_instr;
    }

    bool discontinuous = pc_checker.is_discontinuous(pc, instr);
    ftq.enqueue(pc, instr, next_pc, next_instr, discontinuous, trace_fetch.get_line_read_count());
    return true;
  }

  ftq.set_final();
  return false;
}

static void ensure_readable(size_t count) {
  while (ftq.readable_size() < count && !ftq.final()) {
    if (!fetch_next_trace_line()) {
      break;
    }
  }
}

static bool redirect_to_target(uint32_t redirect_pc, uint32_t target_pc) {
  std::optional<size_t> redirect_index = ftq.find_latest_pc_before_read(redirect_pc);
  size_t search_start = redirect_index ? *redirect_index + 1 : ftq.get_read_index();

  std::optional<size_t> target_index = ftq.find_pc_from(search_start, target_pc);
  while (!target_index && !ftq.final()) {
    if (!fetch_next_trace_line()) {
      break;
    }
    target_index = ftq.find_pc_from(search_start, target_pc);
  }

  if (!target_index) {
    std::fprintf(stderr,
                 "simfrontend: redirect target pc 0x%08x was not found after redirect pc 0x%08x "
                 "(read=%zu, cached=%zu).\n",
                 target_pc, redirect_pc, ftq.get_read_index(), ftq.size());
    return false;
  }

  return ftq.set_read_index(*target_index);
}
extern "C" bool init_galois_sim_frontend(const char *trace_file) {
  if (trace_file == nullptr || trace_file[0] == '\0') {
    std::fprintf(stderr, "simfrontend: empty trace file path.\n");
    return false;
  }
  if (initialized) {
    return true;
  }
  initialized = trace_fetch.init(std::string(trace_file));
  if (initialized) {
    std::fprintf(stderr, "simfrontend: loaded trace %s\n", trace_file);
  }
  return initialized;
}

extern "C" void GaloisSimFrontFetch(int offset, int *valid, int *pc, int *instr, int *preDecode) {
  require_initialized();
  if (offset < 0) {
    *valid = 0;
    *pc = 0;
    *instr = 0;
    *preDecode = 0;
    return;
  }

  ensure_readable(static_cast<size_t>(offset) + 1);
  std::optional<FTQEntry> entry = ftq.peek_offset(static_cast<size_t>(offset));
  if (!entry) {
    *valid = 0;
    *pc = 0;
    *instr = 0;
    *preDecode = 0;
    return;
  }

  *valid = 1;
  *pc = static_cast<int>(entry->pc);
  *instr = static_cast<int>(entry->instr);
  *preDecode = static_cast<int>(entry->packed_data);
}

extern "C" void GaloisSimFrontUpdatePtr(int updateCount) {
  require_initialized();
  if (updateCount <= 0) {
    return;
  }
  ensure_readable(static_cast<size_t>(updateCount));
  if (!ftq.advance_read(static_cast<size_t>(updateCount))) {
    std::fprintf(stderr, "simfrontend: cannot advance read pointer by %d at read=%zu cached=%zu.\n", updateCount,
                 ftq.get_read_index(), ftq.size());
    std::abort();
  }
}

extern "C" void GaloisSimFrontRedirect(int redirectValid, int redirectPc, int redirectTarget, int redirectType) {
  require_initialized();
  if (redirectValid == 0) {
    return;
  }

  uint32_t redirect_pc = static_cast<uint32_t>(redirectPc);
  uint32_t target_pc = static_cast<uint32_t>(redirectTarget);
  if (!redirect_to_target(redirect_pc, target_pc)) {
    std::fprintf(stderr, "simfrontend: redirect failed, type=%d pc=0x%08x target=0x%08x.\n", redirectType,
                 redirect_pc, target_pc);
    std::abort();
  }
}

extern "C" void GaloisSimFrontRobCommit(int valid, int pc) {
  require_initialized();
  if (valid == 0) {
    return;
  }
  ftq.commit(static_cast<uint32_t>(pc));
}
