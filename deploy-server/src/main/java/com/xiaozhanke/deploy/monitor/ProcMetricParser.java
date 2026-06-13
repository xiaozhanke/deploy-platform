package com.xiaozhanke.deploy.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * {@code /proc} 原始输出解析 + 服务端 CPU 差值计算。
 *
 * <p>所有命令只读 Linux 内核 {@code /proc} 文件系统，不依赖 {@code top}/{@code free}/{@code vmstat}
 * 等用户态工具，兼容 Ubuntu/CentOS/Alpine(BusyBox)。解析失败一律返回 {@code null}（由调用方转成
 * 前端的 {@code --} 不可用态），不返回误导性的 {@code 0}。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
public class ProcMetricParser {

    /**
     * 取 {@code /proc/stat} 第一行 CPU 累计 Jiffies。命令 &lt; 10ms、不含 sleep。
     */
    public static final String CPU_STAT_COMMAND = "cat /proc/stat | head -1";

    /**
     * 直接由 {@code /proc/meminfo} 算出内存使用率百分比。{@code MemAvailable} 内核 3.14+ 提供，
     * 比 {@code free} 的「已用」口径更准（计入可回收缓存）。
     *
     * <p>仅在 {@code MemTotal} 与 {@code MemAvailable} 都读到时才输出数值；缺 {@code MemAvailable}
     * （内核 &lt;3.14 等）时输出空串，由调用方降级为 {@code null} 不可用态，而非把未赋值的 a 当 0 误报 100%。
     */
    public static final String MEMORY_USAGE_COMMAND =
            "awk '/MemTotal/{t=$2} /MemAvailable/{a=$2;ok=1} END{if(t>0&&ok)printf \"%.1f\",(t-a)*100/t}' /proc/meminfo";

    /**
     * 解析 {@code /proc/stat} 第一行为 {@link CpuStat}。
     *
     * <p>行形如 {@code cpu  user nice system idle iowait irq softirq steal guest guest_nice}：
     * 第 1 个 token 为标签 {@code cpu}，其后为各状态累计 Jiffies。{@code idle} 取第 4 个数值字段（仅 idle 本列）。
     *
     * <p>{@code total} 只累加 {@code user..steal}（前 8 个数值字段），<strong>不</strong>计入末尾的
     * {@code guest}/{@code guest_nice}——内核已把 {@code guest} 计进 {@code user}、{@code guest_nice} 计进
     * {@code nice}，再次累加会重复计数、膨胀 {@code total} 并系统性低估 CPU 利用率（在跑虚拟机的宿主上尤甚），
     * 致「CPU&gt;80% 异常上浮」阈值更难触发。
     *
     * @param procStatFirstLine {@code cat /proc/stat | head -1} 的标准输出
     * @return 解析结果；格式非法/字段不足/非数字时返回 {@code null}
     */
    public CpuStat parseCpuStat(String procStatFirstLine) {
        if (!StringUtils.hasText(procStatFirstLine)) {
            return null;
        }
        String[] tokens = procStatFirstLine.trim().split("\\s+");
        // tokens[0] 为 "cpu" 标签，至少需要到 idle（第 4 个数值字段）才能计算
        if (tokens.length < 5 || !"cpu".equals(tokens[0])) {
            log.debug("无法识别的 /proc/stat 首行: {}", procStatFirstLine);
            return null;
        }
        try {
            long total = 0L;
            long idle = 0L;
            // 数值字段：i=1 user, 2 nice, 3 system, 4 idle, 5 iowait, 6 irq, 7 softirq, 8 steal,
            // 9 guest, 10 guest_nice。total 只累加 1..8（含），跳过 guest/guest_nice 避免与 user/nice 重复计数。
            int lastBusyIndex = Math.min(tokens.length - 1, 8);
            for (int i = 1; i <= lastBusyIndex; i++) {
                long value = Long.parseLong(tokens[i]);
                total += value;
                if (i == 4) {
                    idle = value;
                }
            }
            return new CpuStat(idle, total);
        } catch (NumberFormatException e) {
            log.debug("解析 /proc/stat 数值失败: {}", procStatFirstLine);
            return null;
        }
    }

    /**
     * 由两次 {@link CpuStat} 快照差值计算 CPU 利用率百分比：{@code usage = 1 - idleDelta / totalDelta}
     * （除 idle 外所有字段均计入「忙」；<strong>不</strong>用 {@code (user+system)/total}）。
     *
     * @param previous 上一周期快照（首轮为 {@code null}）
     * @param current  本周期快照
     * @return 利用率百分比（0–100，保留 1 位小数）；上一次快照缺失、无时间推进或计数器回绕时返回 {@code null}
     */
    public Double computeCpuUsagePercent(CpuStat previous, CpuStat current) {
        if (previous == null || current == null) {
            return null;
        }
        long idleDelta = current.idle() - previous.idle();
        long totalDelta = current.total() - previous.total();
        // totalDelta<=0：无时间推进或计数器回绕；idleDelta<0：通常为重启导致的计数器重置——均判为不可用
        if (totalDelta <= 0 || idleDelta < 0) {
            return null;
        }
        double usage = 1.0 - (double) idleDelta / totalDelta;
        return clampPercent(usage * 100.0);
    }

    /**
     * 解析内存使用率百分比（{@link #MEMORY_USAGE_COMMAND} 的输出，形如 {@code 37.2}）。
     *
     * @param awkOutput awk 命令标准输出
     * @return 内存使用率百分比（0–100，保留 1 位小数）；空/非数字时返回 {@code null}
     */
    public Double parseMemoryUsagePercent(String awkOutput) {
        if (!StringUtils.hasText(awkOutput)) {
            return null;
        }
        // 只取首个 token，容忍命令输出尾随换行或多余空白
        String token = awkOutput.trim().split("\\s+")[0];
        try {
            return clampPercent(Double.parseDouble(token));
        } catch (NumberFormatException e) {
            log.debug("解析内存使用率失败: {}", awkOutput);
            return null;
        }
    }

    /**
     * 百分比落到 [0,100] 并保留 1 位小数（HALF_UP）。
     */
    private Double clampPercent(double percent) {
        double bounded = Math.max(0.0, Math.min(100.0, percent));
        return BigDecimal.valueOf(bounded).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
