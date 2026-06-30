#include "ftq.h"

#include <algorithm>
#include <cassert>
#include <charconv>
#include <cstdio>

static std::string_view trim(std::string_view s) {
  while (!s.empty() && (s.front() == ' ' || s.front() == '\t' || s.front() == '\r')) {
    s.remove_prefix(1);
  }
  while (!s.empty() && (s.back() == ' ' || s.back() == '\t' || s.back() == '\r')) {
    s.remove_suffix(1);
  }
  return s;
}

static std::string_view drop_hex_prefix(std::string_view s) {
  if (s.size() >= 2 && s[0] == '0' && (s[1] == 'x' || s[1] == 'X')) {
    s.remove_prefix(2);
  }
  return s;
}

bool is_rvc(uint32_t instr) {
  return (instr & 0x3) != 0x3;
}

static bool is_link_register(uint32_t reg_idx) {
  return reg_idx == 1 || reg_idx == 5;
}

static BrType get_br_type(uint32_t instr, bool rvc) {
  if (rvc) {
    uint32_t opcode = instr & 0x3;
    uint32_t funct3 = (instr >> 13) & 0x7;
    if (opcode == 0b01) {
      if (funct3 == 0b101) {
        return BrType::Jal;
      }
      if (funct3 == 0b110 || funct3 == 0b111) {
        return BrType::Branch;
      }
    } else if (opcode == 0b10) {
      uint32_t funct5 = (instr >> 2) & 0x1f;
      if (funct3 == 0b100 && funct5 == 0b00000) {
        return BrType::Jalr;
      }
    }
  } else {
    uint32_t opcode = instr & 0x7f;
    uint32_t funct3 = (instr >> 12) & 0x7;
    if (opcode == 0b1100011) {
      return BrType::Branch;
    }
    if (opcode == 0b1101111) {
      return BrType::Jal;
    }
    if (opcode == 0b1100111 && funct3 == 0b000) {
      return BrType::Jalr;
    }
  }
  return BrType::NotCfi;
}

bool parse_line(std::string_view line, uint32_t &pc, uint32_t &instr) {
  line = trim(line);
  if (line.empty()) {
    return false;
  }

  size_t colon_pos = line.find(':');
  if (colon_pos == std::string_view::npos) {
    std::fprintf(stderr, "simfrontend: bad trace line without ':' : %.*s\n", static_cast<int>(line.size()), line.data());
    std::abort();
  }

  std::string_view pc_sv = drop_hex_prefix(trim(line.substr(0, colon_pos)));
  std::string_view instr_sv = drop_hex_prefix(trim(line.substr(colon_pos + 1)));
  if (pc_sv.empty() || instr_sv.empty()) {
    return false;
  }

  auto pc_res = std::from_chars(pc_sv.data(), pc_sv.data() + pc_sv.size(), pc, 16);
  auto instr_res = std::from_chars(instr_sv.data(), instr_sv.data() + instr_sv.size(), instr, 16);
  if (pc_res.ec != std::errc() || pc_res.ptr != pc_sv.data() + pc_sv.size() || instr_res.ec != std::errc() ||
      instr_res.ptr != instr_sv.data() + instr_sv.size()) {
    std::fprintf(stderr, "simfrontend: failed to parse trace line: %.*s\n", static_cast<int>(line.size()), line.data());
    std::abort();
  }
  return true;
}

RiscvInstructionInfo analyze_instruction(uint32_t instr) {
  RiscvInstructionInfo info;
  info.isRVC = is_rvc(instr);
  info.brType = get_br_type(instr, info.isRVC);

  if (info.isRVC) {
    info.rd = (instr >> 7) & 0x1f;
    info.rs1 = (instr >> 7) & 0x1f;
  } else {
    info.rd = (instr >> 7) & 0x1f;
    info.rs1 = (instr >> 15) & 0x1f;
  }

  bool is_jal_not_rvc = info.brType == BrType::Jal && !info.isRVC;
  bool is_jalr = info.brType == BrType::Jalr;
  info.isCall = (is_jal_not_rvc || is_jalr) && is_link_register(info.rd);
  info.isRet = is_jalr && is_link_register(info.rs1) && !info.isCall;
  info.isJump = info.brType == BrType::Jal || info.brType == BrType::Jalr;
  return info;
}

static void set_field(uint32_t &data, uint32_t value, int position, int width) {
  uint32_t mask = ((1u << width) - 1u) << position;
  data = (data & ~mask) | ((value << position) & mask);
}

uint32_t pack_trace_data(const RiscvInstructionInfo &info, bool pred_taken, bool is_last_ftq_entry, bool wrap,
                         uint32_t queue_id, uint32_t offset) {
  uint32_t packed = 0;
  set_field(packed, 1, POS_CONST, 1);
  set_field(packed, info.isRVC ? 1 : 0, POS_IS_RVC, 1);
  set_field(packed, static_cast<uint32_t>(info.brType), POS_BR_TYPE, 2);
  set_field(packed, info.isCall ? 1 : 0, POS_IS_CALL, 1);
  set_field(packed, info.isRet ? 1 : 0, POS_IS_RET, 1);
  set_field(packed, pred_taken ? 1 : 0, POS_PRED_TAKEN, 1);
  set_field(packed, queue_id & 0x3f, POS_QUEUE_ID, 6);
  set_field(packed, wrap ? 1 : 0, POS_QUEUE_ID_WRAP, 1);
  set_field(packed, is_last_ftq_entry ? 1 : 0, POS_IS_LAST_ENTRY, 1);
  set_field(packed, offset & 0x1f, POS_OFFSET, 5);
  return packed;
}

bool ContinuityChecker::is_discontinuous(uint32_t current_pc, uint32_t current_instr) {
  if (first_) {
    first_ = false;
    last_pc_ = current_pc;
    last_instr_was_rvc_ = is_rvc(current_instr);
    return true;
  }

  uint32_t expected = last_pc_ + (last_instr_was_rvc_ ? 2 : 4);
  bool discontinuous = current_pc != expected;
  last_pc_ = current_pc;
  last_instr_was_rvc_ = is_rvc(current_instr);
  return discontinuous;
}

bool ContinuityChecker::is_discontinuous(uint32_t current_pc, bool current_instr_is_rvc, uint32_t next_pc) {
  uint32_t expected = current_pc + (current_instr_is_rvc ? 2 : 4);
  return next_pc != expected;
}

void FetchTargetQueue::start_new_group() {
  if (has_group_) {
    current_group_id_++;
    if (current_group_id_ >= ID_QUEUE_SIZE) {
      current_group_id_ = 0;
      current_group_wrap_ = !current_group_wrap_;
    }
  }
  has_group_ = true;
  current_group_start_ = entries_.size();
  current_group_instr_count_ = 0;
  accumulated_bytes_ = 0;
}

bool FetchTargetQueue::will_start_new_group(bool is_discontinuous, uint32_t next_bytes) const {
  return !has_group_ || need_new_group_ || is_discontinuous || accumulated_bytes_ + next_bytes > FETCH_BLOCK_BYTES ||
         current_group_instr_count_ >= MAX_INSTRS_PER_GROUP;
}

bool FetchTargetQueue::enqueue(uint32_t pc, uint32_t instr, uint32_t next_pc, uint32_t next_instr,
                               bool is_discontinuous, uint64_t line_number) {
  uint32_t instr_bytes = is_rvc(instr) ? 2 : 4;
  if (will_start_new_group(is_discontinuous, instr_bytes)) {
    start_new_group();
  }

  RiscvInstructionInfo info = analyze_instruction(instr);
  bool pred_taken = ContinuityChecker::is_discontinuous(pc, info.isRVC, next_pc);
  uint32_t next_bytes = instr_bytes + (is_rvc(next_instr) ? 2 : 4);
  bool is_last = pred_taken || accumulated_bytes_ + next_bytes > FETCH_BLOCK_BYTES || info.isJump;
  uint32_t group_start_pc = entries_.empty() || current_group_start_ >= entries_.size() ? pc : entries_[current_group_start_].pc;
  uint32_t offset = pc >= group_start_pc ? ((pc - group_start_pc) >> 1) : 0;
  uint32_t packed = pack_trace_data(info, pred_taken, is_last, current_group_wrap_, current_group_id_, offset);

  entries_.push_back({pc, instr, packed, current_group_id_, current_group_wrap_, line_number});
  current_group_instr_count_++;
  accumulated_bytes_ += instr_bytes;
  need_new_group_ = info.isJump || pred_taken;
  return true;
}

std::optional<FTQEntry> FetchTargetQueue::peek_offset(size_t offset) const {
  size_t index = read_index_ + offset;
  if (index >= entries_.size()) {
    return std::nullopt;
  }
  return entries_[index];
}

bool FetchTargetQueue::advance_read(size_t count) {
  if (read_index_ + count > entries_.size()) {
    return false;
  }
  read_index_ += count;
  return true;
}

bool FetchTargetQueue::set_read_index(size_t index) {
  if (index > entries_.size()) {
    return false;
  }
  read_index_ = index;
  return true;
}

bool FetchTargetQueue::commit(uint32_t pc) {
  size_t end = std::min(read_index_, entries_.size());
  for (size_t i = end; i > commit_index_; --i) {
    size_t index = i - 1;
    if (entries_[index].pc == pc) {
      commit_index_ = index + 1;
      return true;
    }
  }
  return false;
}

void FetchTargetQueue::set_final() {
  final_ = true;
}

size_t FetchTargetQueue::readable_size() const {
  return entries_.size() - read_index_;
}

size_t FetchTargetQueue::get_read_index() const {
  return read_index_;
}

size_t FetchTargetQueue::get_commit_index() const {
  return commit_index_;
}

size_t FetchTargetQueue::size() const {
  return entries_.size();
}

bool FetchTargetQueue::final() const {
  return final_;
}

std::optional<size_t> FetchTargetQueue::find_latest_pc_before_read(uint32_t pc) const {
  size_t end = std::min(read_index_, entries_.size());
  for (size_t i = end; i > 0; --i) {
    size_t index = i - 1;
    if (entries_[index].pc == pc) {
      return index;
    }
  }
  return std::nullopt;
}

std::optional<size_t> FetchTargetQueue::find_pc_from(size_t start, uint32_t pc) const {
  for (size_t i = start; i < entries_.size(); ++i) {
    if (entries_[i].pc == pc) {
      return i;
    }
  }
  return std::nullopt;
}
