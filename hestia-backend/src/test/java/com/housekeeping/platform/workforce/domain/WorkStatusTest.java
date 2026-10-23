package com.housekeeping.platform.workforce.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 人员状态机测试（docs/modules/05 第 5.1 节）。
 */
class WorkStatusTest {

    @Nested
    @DisplayName("合法迁移")
    class Allowed {

        @Test
        @DisplayName("入职中 -> 在岗")
        void onboardingToActive() {
            assertThat(WorkStatus.ONBOARDING.canTransitionTo(WorkStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("在岗 -> 暂停 / 离职")
        void activeToSuspendedOrLeft() {
            assertThat(WorkStatus.ACTIVE.canTransitionTo(WorkStatus.SUSPENDED)).isTrue();
            assertThat(WorkStatus.ACTIVE.canTransitionTo(WorkStatus.LEFT)).isTrue();
        }

        @Test
        @DisplayName("暂停 -> 恢复 / 离职")
        void suspendedToActiveOrLeft() {
            assertThat(WorkStatus.SUSPENDED.canTransitionTo(WorkStatus.ACTIVE)).isTrue();
            assertThat(WorkStatus.SUSPENDED.canTransitionTo(WorkStatus.LEFT)).isTrue();
        }
    }

    @Nested
    @DisplayName("非法迁移")
    class Rejected {

        @Test
        @DisplayName("离职是终态，不能回到任何状态")
        void leftIsTerminal() {
            assertThat(WorkStatus.LEFT.isTerminal()).isTrue();
            assertThat(WorkStatus.LEFT.allowedTargets()).isEmpty();
            assertThat(WorkStatus.LEFT.canTransitionTo(WorkStatus.ACTIVE)).isFalse();
            assertThat(WorkStatus.LEFT.canTransitionTo(WorkStatus.ONBOARDING)).isFalse();
        }

        @Test
        @DisplayName("不能从入职中直接暂停或离职：没入职就没有可暂停的东西")
        void onboardingCannotSkip() {
            assertThat(WorkStatus.ONBOARDING.canTransitionTo(WorkStatus.SUSPENDED)).isFalse();
            assertThat(WorkStatus.ONBOARDING.canTransitionTo(WorkStatus.LEFT)).isFalse();
        }

        @Test
        @DisplayName("在岗不能再迁移到在岗（重复审核应当被拒绝）")
        void activeToActive() {
            assertThat(WorkStatus.ACTIVE.canTransitionTo(WorkStatus.ACTIVE)).isFalse();
        }
    }
}
