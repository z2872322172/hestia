package com.housekeeping.platform.scheduling.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时段状态机测试。
 *
 * <p>状态机是时段并发的规则源头，这里把 5×5 迁移矩阵穷举钉死：
 * 任何一个"看起来合理"的非法迁移（如 BOOKED 直改 BLOCKED）都不该悄悄通过，
 * 否则订单占用的时段会被排班变更静默吞掉。</p>
 */
class SlotStatusTest {

    /** 全部状态，穷举用。 */
    private static final Set<SlotStatus> ALL = Set.of(SlotStatus.values());

    @Test
    @DisplayName("AVAILABLE 只能到 HELD / BLOCKED / CLOSED")
    void availableTransitions() {
        assertThat(SlotStatus.AVAILABLE.canTransitTo(SlotStatus.HELD)).isTrue();
        assertThat(SlotStatus.AVAILABLE.canTransitTo(SlotStatus.BLOCKED)).isTrue();
        assertThat(SlotStatus.AVAILABLE.canTransitTo(SlotStatus.CLOSED)).isTrue();
        assertThat(SlotStatus.AVAILABLE.canTransitTo(SlotStatus.AVAILABLE)).isFalse();
        assertThat(SlotStatus.AVAILABLE.canTransitTo(SlotStatus.BOOKED)).isFalse();
    }

    @Test
    @DisplayName("HELD 可以支付、释放或关闭")
    void heldTransitions() {
        assertThat(SlotStatus.HELD.canTransitTo(SlotStatus.BOOKED)).isTrue();
        assertThat(SlotStatus.HELD.canTransitTo(SlotStatus.AVAILABLE)).isTrue();
        assertThat(SlotStatus.HELD.canTransitTo(SlotStatus.CLOSED)).isTrue();
        assertThat(SlotStatus.HELD.canTransitTo(SlotStatus.BLOCKED)).isFalse();
        assertThat(SlotStatus.HELD.canTransitTo(SlotStatus.HELD)).isFalse();
    }

    @Test
    @DisplayName("BOOKED 只能释放或关闭，不能直改 BLOCKED")
    void bookedTransitions() {
        assertThat(SlotStatus.BOOKED.canTransitTo(SlotStatus.AVAILABLE)).isTrue();
        assertThat(SlotStatus.BOOKED.canTransitTo(SlotStatus.CLOSED)).isTrue();
        assertThat(SlotStatus.BOOKED.canTransitTo(SlotStatus.BLOCKED)).isFalse();
        assertThat(SlotStatus.BOOKED.canTransitTo(SlotStatus.HELD)).isFalse();
        assertThat(SlotStatus.BOOKED.canTransitTo(SlotStatus.BOOKED)).isFalse();
    }

    @Test
    @DisplayName("BLOCKED 只能恢复为 AVAILABLE")
    void blockedTransitions() {
        assertThat(SlotStatus.BLOCKED.canTransitTo(SlotStatus.AVAILABLE)).isTrue();
        for (SlotStatus target : ALL) {
            if (target != SlotStatus.AVAILABLE) {
                assertThat(SlotStatus.BLOCKED.canTransitTo(target)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("CLOSED 是终态")
    void closedIsTerminal() {
        for (SlotStatus target : ALL) {
            assertThat(SlotStatus.CLOSED.canTransitTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("只有 HELD 与 BOOKED 关联订单")
    void occupiedByOrder() {
        assertThat(SlotStatus.AVAILABLE.occupiedByOrder()).isFalse();
        assertThat(SlotStatus.HELD.occupiedByOrder()).isTrue();
        assertThat(SlotStatus.BOOKED.occupiedByOrder()).isTrue();
        assertThat(SlotStatus.BLOCKED.occupiedByOrder()).isFalse();
        assertThat(SlotStatus.CLOSED.occupiedByOrder()).isFalse();
    }

    @Test
    @DisplayName("按数据库值解析状态")
    void parsesStoredValue() {
        assertThat(SlotStatus.of("AVAILABLE")).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(SlotStatus.of("HELD")).isEqualTo(SlotStatus.HELD);
        assertThat(SlotStatus.of("BOOKED")).isEqualTo(SlotStatus.BOOKED);
        assertThat(SlotStatus.of("BLOCKED")).isEqualTo(SlotStatus.BLOCKED);
        assertThat(SlotStatus.of("CLOSED")).isEqualTo(SlotStatus.CLOSED);
    }
}
