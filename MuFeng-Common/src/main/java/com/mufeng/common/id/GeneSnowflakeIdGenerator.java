package com.mufeng.common.id;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 基因法分布式雪花 ID 生成器 (适配单库 16 张分表精准路由)
 *
 * 64位 Bit 结构划分：
 * - 1 bit : 符号位 (固定为 0，保证正数)
 * - 41 bits: 毫秒级时间戳差值 (可使用约 69 年)
 * - 5 bits : 机器节点 ID (0 ~ 31)
 * - 13 bits: 毫秒内自增序列号 (0 ~ 8191，单机每毫秒支持生成 8192 个订单号)
 * - 4 bits : 分片基因位 (固定填入 userId & 15，确保 orderNo % 16 == userId % 16)
 */
@Component
public class GeneSnowflakeIdGenerator {

    /**
     * 起始基准时间：2026-01-01 00:00:00 (毫秒时间戳)
     */
    private static final long EPOCH = 1767225600000L;

    /**
     * 各段占用的位数
     */
    private static final long GENE_BITS = 4L;
    private static final long SEQUENCE_BITS = 13L;
    private static final long WORKER_ID_BITS = 5L;

    /**
     * 最大值掩码
     */
    private static final long GENE_MASK = (1L << GENE_BITS) - 1;       // 15 (0b1111)
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1; // 8191

    /**
     * 左移偏移量
     */
    private static final long SEQUENCE_SHIFT = GENE_BITS;                                // 4
    private static final long WORKER_ID_SHIFT = GENE_BITS + SEQUENCE_BITS;               // 17
    private static final long TIMESTAMP_SHIFT = GENE_BITS + SEQUENCE_BITS + WORKER_ID_BITS; // 22

    /**
     * 当前节点 WorkerId (默认取随机数或环境变量，0 ~ 31)
     */
    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public GeneSnowflakeIdGenerator() {
        this.workerId = ThreadLocalRandom.current().nextLong(0, 32);
    }

    /**
     * 生成带 user_id 4位分表基因的分布式订单号（父订单号 / 子订单号专用）
     *
     * @param userId 买家用户ID
     * @return 末4位与 userId 完全一致的 64位长整型订单号
     */
    public synchronized long nextOrderNo(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("生成基因订单号时 userId 不能为空");
        }
        long currentTimestamp = currentTimeMillis();

        // 防止时钟回拨：若小幅回拨则阻塞等待追平
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            } else {
                throw new IllegalStateException("系统时钟发生严重回拨，拒绝生成订单号，回拨毫秒数: " + offset);
            }
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        // 提取 userId 的最后 4 位二进制作为分表基因 (0 ~ 15)
        long userGene = userId & GENE_MASK;

        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | (sequence << SEQUENCE_SHIFT)
                | userGene;
    }

    /**
     * 生成普通分布式主键 ID（用于无需分表的快照表、优惠券表、结算单表）
     */
    public synchronized long nextId() {
        return nextOrderNo(ThreadLocalRandom.current().nextLong(0, 16));
    }

    /**
     * 提取目标 ID (无论传入的是 userId 还是 orderNo) 对应的 16 分表索引 (0 ~ 15)
     */
    public static int extractTableShardIndex(Long idOrOrderNo) {
        return (int) (idOrOrderNo & GENE_MASK);
    }

    /**
     * 提取格式化后的分表后缀 ("00" ~ "15")
     */
    public static String extractTableSuffix(Long idOrOrderNo) {
        int index = extractTableShardIndex(idOrOrderNo);
        return index < 10 ? "0" + index : String.valueOf(index);
    }

    private long waitNextMillis(long lastTs) {
        long ts = currentTimeMillis();
        while (ts <= lastTs) {
            ts = currentTimeMillis();
        }
        return ts;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}