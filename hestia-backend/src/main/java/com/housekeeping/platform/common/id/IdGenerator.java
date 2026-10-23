package com.housekeeping.platform.common.id;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局唯一 ID 生成器，输出 26 位 ULID（Crockford Base32）。
 *
 * <p>为什么不用自增主键作为对外标识：订单号、事件 ID、traceId 一旦对外暴露，
 * 自增序列会泄露业务量并让枚举攻击变得容易；ULID 前 48 位是毫秒时间戳，
 * 天然按时间有序，既能做索引友好排序，又不需要像 UUID 那样随机打散写入。</p>
 *
 * <p>本类不依赖 Spring，可被领域层与基础设施层共同使用。</p>
 */
public final class IdGenerator {

    /** Crockford Base32：去掉了 I、L、O、U，避免人工抄写歧义。 */
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** ULID 固定长度：10 位时间戳 + 16 位随机数。 */
    private static final int ULID_LENGTH = 26;

    private static final int TIMESTAMP_CHARS = 10;

    private static final int RANDOM_BYTES = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 记录上次生成时间，保证同一毫秒内不会出现时间戳回退，
     * 使 ID 在单机内保持字典序递增。
     */
    private static final AtomicLong LAST_TIMESTAMP = new AtomicLong();

    private IdGenerator() {
    }

    /**
     * 生成一个新的 ULID。
     *
     * @return 26 位大写字母数字字符串，例如 {@code 01JABCDEFG0123456789ABCDEF}
     */
    public static String nextId() {
        long timestamp = System.currentTimeMillis();
        long previous = LAST_TIMESTAMP.get();
        while (timestamp < previous && !LAST_TIMESTAMP.compareAndSet(previous, previous)) {
            previous = LAST_TIMESTAMP.get();
        }
        if (timestamp < previous) {
            timestamp = previous;
        }
        LAST_TIMESTAMP.set(timestamp);

        char[] buffer = new char[ULID_LENGTH];

        // 48 位毫秒时间戳编码为前 10 个字符（最高位不足 5 位，剩余位补 0）
        long remaining = timestamp;
        for (int index = TIMESTAMP_CHARS - 1; index >= 0; index--) {
            buffer[index] = CROCKFORD[(int) (remaining & 0x1F)];
            remaining >>>= 5;
        }

        // 80 位随机数编码为后 16 个字符
        byte[] randomBytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(randomBytes);
        int bitBuffer = 0;
        int bitCount = 0;
        int cursor = TIMESTAMP_CHARS;
        for (byte value : randomBytes) {
            bitBuffer = (bitBuffer << 8) | (value & 0xFF);
            bitCount += 8;
            while (bitCount >= 5 && cursor < ULID_LENGTH) {
                buffer[cursor++] = CROCKFORD[(bitBuffer >>> (bitCount - 5)) & 0x1F];
                bitCount -= 5;
            }
        }
        return new String(buffer);
    }
}
