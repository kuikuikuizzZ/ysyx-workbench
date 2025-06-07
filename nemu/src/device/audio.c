/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include <common.h>
#include <device/map.h>
#include <SDL2/SDL.h>

enum {
  reg_freq,
  reg_channels,
  reg_samples,
  reg_sbuf_size,
  reg_init,
  reg_count,
  nr_reg
};

static uint8_t *sbuf = NULL;
static uint32_t *audio_base = NULL;

int queue_front = 0;           // 队头索引（指向待读取位置）
int queue_rear = 0;            // 队尾索引（指向待写入位置）
/* 判断队列是否为空 */
int queue_is_empty() {
    return (queue_front == queue_rear);
}

/* 判断队列是否已满 */
int queue_is_full() {
    return ((queue_rear + 1) % CONFIG_SB_SIZE == queue_front);
}

/* 入队操作 */
int queue_enqueue(int value) {
    if (queue_is_full()) return 0;  // 队列满时返回0
    sbuf[queue_rear] = value;
    queue_rear = (queue_rear + 1) % CONFIG_SB_SIZE;  // 循环移动尾指针
    return 1;  // 入队成功
}

/* 批量出队操作（支持多元素读取）*/
int queue_dequeue_batch(int *output, int num) {
    if (queue_is_empty() || num <= 0) return 0;  // 空队列或无效数量
    
    int count = 0;
    while (count < num && !queue_is_empty()) {
        output[count] = sbuf[queue_front];
        queue_front = (queue_front + 1) % CONFIG_SB_SIZE;  // 循环移动头指针
        count++;
    }
    return count;  // 返回实际出队的元素数量
}

static void audio_io_handler(uint32_t offset, int len, bool is_write) {

}

static void audio_play(void *userdata, uint8_t *stream, int len) {
  int nread = len;
  while(audio_base[reg_count]>0){
    if (audio_base[reg_count] < len) nread = audio_base[reg_count] ;
    memcpy(stream,sbuf, nread);
    audio_base[reg_count] -= nread;
  }
  if (len > nread) {
    memset(stream + nread, 0, len - nread);
  }
  memset(sbuf, 0, audio_base[reg_sbuf_size]);
}

void init_audio_ctrl(u_int32_t* audio_base) {
  assert(audio_base != NULL);

  SDL_AudioSpec s = {};
  s.freq = audio_base[reg_freq];
  s.format = AUDIO_S16SYS;
  s.channels = audio_base[reg_channels];
  s.samples = audio_base[reg_samples];
  s.callback = audio_play;
  s.userdata = NULL;
  int ret = SDL_InitSubSystem(SDL_INIT_AUDIO);
  if (ret == 0) {
    SDL_OpenAudio(&s, NULL);
    SDL_PauseAudio(0);
  }
}
void init_audio() {
  printf("Initializing audio device...\n");
  uint32_t space_size = sizeof(uint32_t) * nr_reg;
  audio_base = (uint32_t *)new_space(space_size);
#ifdef CONFIG_HAS_PORT_IO
  add_pio_map ("audio", CONFIG_AUDIO_CTL_PORT, audio_base, space_size, audio_io_handler);
#else
  add_mmio_map("audio", CONFIG_AUDIO_CTL_MMIO, audio_base, space_size, audio_io_handler);
#endif

  sbuf = (uint8_t *)new_space(CONFIG_SB_SIZE);
  add_mmio_map("audio-sbuf", CONFIG_SB_ADDR, sbuf, CONFIG_SB_SIZE, NULL);
  init_audio_ctrl(audio_base);
  audio_base[reg_init]=0;
  audio_base[reg_sbuf_size] = CONFIG_SB_SIZE;
}
