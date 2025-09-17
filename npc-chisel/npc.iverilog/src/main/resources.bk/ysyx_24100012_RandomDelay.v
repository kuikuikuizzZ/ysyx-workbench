module ysyx_24100012_lfsr_delay (
    input clk,          // 时钟信号
    input rst_n,        // 异步复位 (低有效)
    input trigger,      // 触发信号 (高电平触发)
    output reg done     // 延迟完成信号
);
    // 4位LFSR：可生成1-15的随机延迟周期数
    reg [3:0] lfsr = 4'b0001;    // 初始种子值 (不可为0)
    reg [3:0] delay_count;        // 当前延迟计数器
    reg [3:0] target_delay;       // 目标延迟值
    reg active;                   // 状态机标志 (0=空闲, 1=延迟中)

    // LFSR伪随机数生成器
    wire feedback = lfsr[3] ^ lfsr[2]; // 反馈抽头 (4位LFSR多项式: x^4 + x^3 + 1)
    
    always @(posedge clk ) begin
        if (!rst_n) begin
            lfsr <= 4'b0001;      // 复位时重置LFSR
            active <= 1'b0;       // 空闲状态
            delay_count <= 4'b0;  // 计数器清零
            target_delay <= 4'b0; // 目标值清零
            done <= 1'b0;         // 输出复位
        end 
        else begin
            // LFSR更新逻辑 (仅在空闲时更新)
            if (!active) begin
                lfsr <= {lfsr[2:0], feedback};
            end
            
            if (trigger && !active) begin  // 触发新延迟
                active <= 1'b1;           // 进入延迟状态
                done <= 1'b0;             // 清除完成标志
                delay_count <= 4'b0;      // 计数器重置
                target_delay <= lfsr;     // 设置随机延迟值
            end 
            else if (active) begin        // 延迟进行中
                if (delay_count == target_delay) begin
                    active <= 1'b0;        // 返回空闲状态
                    done <= 1'b1;           // 延迟结束信号
                end 
                else begin
                    delay_count <= delay_count + 1; // 计数器递增
                end
            end 
            else begin
                done <= 1'b0;              // 非延迟状态下输出0
            end
        end
    end
endmodule
