#ifndef NPC_GALOIS_SIMFRONTEND_FTQ_H
#define NPC_GALOIS_SIMFRONTEND_FTQ_H

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string_view>
#include <vector>

enum class BrType {
  NotCfi,
  Branch,
  Jal,
  Jalr,
};

enum TraceBitPositions {
  POS_CONST = 0,
  POS_IS_RVC = 1,
  POS_BR_TYPE = 2,
  POS_IS_CALL = 4,
  POS_IS_RET = 5,
  POS_PRED_TAKEN = 6,
  POS_QUEUE_ID = 7,
  POS_QUEUE_ID_WRAP = 13,
  POS_IS_LAST_ENTRY = 14,
  POS_OFFSET = 15,
};

struct RiscvInstructionInfo {
  bool isRVC = false;
  BrType brType = BrType::NotCfi;
  bool isCall = false;
  bool isRet = false;
  bool isJump = false;
  uint32_t rd = 0;
  uint32_t rs1 = 0;
};

struct FTQEntry {
  uint32_t pc = 0;
  uint32_t instr = 0;
  uint32_t packed_data = 0;
  size_t fetch_group_id = 0;
  bool fetch_group_wrap = false;
  uint64_t line_number = 0;
};

struct FetchGroupInfo {
  bool is_valid = false;
  size_t start_index = 0;
  size_t instr_count = 0;
  bool wrap_bit = false;
};

bool is_rvc(uint32_t instr);
bool parse_line(std::string_view line, uint32_t &pc, uint32_t &instr);
RiscvInstructionInfo analyze_instruction(uint32_t instr);
uint32_t pack_trace_data(const RiscvInstructionInfo &info, bool pred_taken, bool is_last_ftq_entry, bool wrap,
                         uint32_t queue_id, uint32_t offset);

class ContinuityChecker {
public:
  bool is_discontinuous(uint32_t current_pc, uint32_t current_instr);
  static bool is_discontinuous(uint32_t current_pc, bool current_instr_is_rvc, uint32_t next_pc);

private:
  uint32_t last_pc_ = 0;
  bool last_instr_was_rvc_ = false;
  bool first_ = true;
};

class FetchTargetQueue {
public:
  static constexpr size_t ID_QUEUE_SIZE = 64;
  static constexpr size_t MAX_INSTRS_PER_GROUP = 16;
  static constexpr uint32_t FETCH_BLOCK_BYTES = 32;

  bool enqueue(uint32_t pc, uint32_t instr, uint32_t next_pc, uint32_t next_instr, bool is_discontinuous,
               uint64_t line_number);

  std::optional<FTQEntry> peek_offset(size_t offset) const;
  bool advance_read(size_t count);
  bool set_read_index(size_t index);
  bool commit(uint32_t pc);
  void set_final();

  size_t readable_size() const;
  size_t get_read_index() const;
  size_t get_commit_index() const;
  size_t size() const;
  bool final() const;

  std::optional<size_t> find_latest_pc_before_read(uint32_t pc) const;
  std::optional<size_t> find_pc_from(size_t start, uint32_t pc) const;

private:
  void start_new_group();
  bool will_start_new_group(bool is_discontinuous, uint32_t next_bytes) const;

  std::vector<FTQEntry> entries_;
  size_t read_index_ = 0;
  size_t commit_index_ = 0;

  size_t current_group_id_ = 0;
  bool current_group_wrap_ = false;
  size_t current_group_start_ = 0;
  size_t current_group_instr_count_ = 0;
  uint32_t accumulated_bytes_ = 0;
  bool has_group_ = false;
  bool need_new_group_ = false;
  bool final_ = false;
};

#endif
