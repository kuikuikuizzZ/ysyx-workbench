#ifndef NPC_GALOIS_SIMFRONTEND_TRACEREADER_H
#define NPC_GALOIS_SIMFRONTEND_TRACEREADER_H

#include <cstddef>
#include <string>
#include <string_view>

class MmapLineReader {
public:
  MmapLineReader() = default;
  explicit MmapLineReader(const std::string &filename);
  ~MmapLineReader();

  bool init(const std::string &filename);
  bool get_next_line(std::string_view &line);
  bool probe_next_line(std::string_view &line) const;
  size_t get_line_read_count() const;

  bool is_final() const;
  bool is_open() const;

private:
  MmapLineReader(const MmapLineReader &) = delete;
  MmapLineReader &operator=(const MmapLineReader &) = delete;

  int fd_ = -1;
  char *mapped_data_ = nullptr;
  size_t file_size_ = 0;
  const char *read_ptr_ = nullptr;
  const char *end_of_file_ = nullptr;
  size_t lines_read_ = 0;
  bool final_ = false;
  bool open_ = false;
};

#endif
