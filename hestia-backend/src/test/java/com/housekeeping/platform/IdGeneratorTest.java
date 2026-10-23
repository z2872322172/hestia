package com.housekeeping.platform;

import com.housekeeping.platform.common.id.IdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ULID 生成器测试。
 */
class IdGeneratorTest {

    private static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    @Test
    @DisplayName("ID 长度为 26 且字符集合法")
    void idHasExpectedFormat() {
        String id = IdGenerator.nextId();
        assertThat(id).hasSize(26);
        assertThat(id.chars().mapToObj(c -> String.valueOf((char) c)))
                .allMatch(CROCKFORD_ALPHABET::contains);
    }

    @Test
    @DisplayName("批量生成不重复")
    void idsAreUnique() {
        Set<String> ids = new HashSet<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            ids.add(IdGenerator.nextId());
        }
        assertThat(ids).hasSize(10_000);
    }

    @Test
    @DisplayName("时间戳前缀保证 ID 按时间递增")
    void idsAreTimeOrdered() throws InterruptedException {
        String first = IdGenerator.nextId();
        Thread.sleep(5L);
        String second = IdGenerator.nextId();
        assertThat(first.compareTo(second)).isLessThan(0);
    }
}
