#include "tracereader.h"

#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

MmapLineReader::MmapLineReader(const std::string &filename) {
  init(filename);
}

MmapLineReader::~MmapLineReader() {
  if (mapped_data_ != nullptr) {
    munmap(mapped_data_, file_size_);
  }
  if (fd_ != -1) {
    close(fd_);
  }
}

bool MmapLineReader::init(const std::string &filename) {
  fd_ = open(filename.c_str(), O_RDONLY);
  if (fd_ == -1) {
    perror("simfrontend open trace");
    return false;
  }

  struct stat sb;
  if (fstat(fd_, &sb) == -1) {
    perror("simfrontend fstat trace");
    close(fd_);
    fd_ = -1;
    return false;
  }

  file_size_ = static_cast<size_t>(sb.st_size);
  if (file_size_ == 0) {
    open_ = true;
    final_ = true;
    return true;
  }

  mapped_data_ = static_cast<char *>(mmap(nullptr, file_size_, PROT_READ, MAP_PRIVATE, fd_, 0));
  if (mapped_data_ == MAP_FAILED) {
    perror("simfrontend mmap trace");
    mapped_data_ = nullptr;
    close(fd_);
    fd_ = -1;
    return false;
  }

  read_ptr_ = mapped_data_;
  end_of_file_ = mapped_data_ + file_size_;
  lines_read_ = 0;
  final_ = false;
  open_ = true;
  return true;
}

bool MmapLineReader::get_next_line(std::string_view &line) {
  if (read_ptr_ == nullptr || read_ptr_ >= end_of_file_) {
    final_ = true;
    return false;
  }

  const char *line_start = read_ptr_;
  const void *newline_ptr = memchr(read_ptr_, '\n', end_of_file_ - read_ptr_);
  const char *line_end = newline_ptr == nullptr ? end_of_file_ : static_cast<const char *>(newline_ptr);
  read_ptr_ = newline_ptr == nullptr ? end_of_file_ : line_end + 1;
  line = std::string_view(line_start, static_cast<size_t>(line_end - line_start));
  lines_read_++;
  return true;
}

bool MmapLineReader::probe_next_line(std::string_view &line) const {
  if (read_ptr_ == nullptr || read_ptr_ >= end_of_file_) {
    return false;
  }

  const char *line_start = read_ptr_;
  const void *newline_ptr = memchr(read_ptr_, '\n', end_of_file_ - read_ptr_);
  const char *line_end = newline_ptr == nullptr ? end_of_file_ : static_cast<const char *>(newline_ptr);
  line = std::string_view(line_start, static_cast<size_t>(line_end - line_start));
  return true;
}

size_t MmapLineReader::get_line_read_count() const {
  return lines_read_;
}

bool MmapLineReader::is_final() const {
  return final_;
}

bool MmapLineReader::is_open() const {
  return open_;
}
