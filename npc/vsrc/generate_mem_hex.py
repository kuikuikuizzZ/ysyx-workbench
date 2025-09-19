# generate_mem_hex.py
import random
import struct

def generate_hex_file(filename, size=0x10000, pattern="zeros"):
    """
    生成内存初始化文件
    size: 内存大小（字节）
    pattern: 初始化模式（zeros, inc, random, custom）
    """
    # 计算32位字的数量
    num_words = size 
    
    with open(filename, 'w') as f:
        # 文件头
        f.write("// Memory initialization file\n")
        f.write("// Size: 0x{:X} bytes\n".format(size))
        f.write("// Pattern: {}\n\n".format(pattern))
        
        # 每16个字一个块
        for block_start in range(0, num_words, 16):
            # 地址标识
            f.write("@{:04X}\n".format(block_start))
            
            # 生成一行16个字
            words = []
            for i in range(16):
                addr = block_start + i
                if addr >= num_words:
                    break
                
                if pattern == "zeros":
                    value = 0
                elif pattern == "inc":
                    value = addr
                elif pattern == "random":
                    value = random.randint(0, 0xFFFFFFFF)
                elif pattern == "custom":
                    # 自定义模式示例：地址的低16位作为高16位
                    value = (addr & 0xFFFF) << 16 | (addr & 0xFFFF)
                else:
                    value = 0
                
                words.append("{:02X}".format(value))
            
            f.write(" ".join(words) + "\n")

# 生成全零初始化文件
generate_hex_file("mem_zeros.hex", 20000, "zeros")