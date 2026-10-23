package com.housekeeping.platform.location.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务区域状态机测试。
 */
class ServiceAreaStatusTest {

    @Test
    @DisplayName("草稿只能先启用")
    void draftCanOnlyActivate() {
        assertThat(ServiceAreaStatus.DRAFT.canTransitionTo(ServiceAreaStatus.ACTIVE)).isTrue();
        assertThat(ServiceAreaStatus.DRAFT.canTransitionTo(ServiceAreaStatus.INACTIVE)).isFalse();
    }

    @Test
    @DisplayName("启用与停用可以互相切换")
    void activeAndInactiveAreReversible() {
        assertThat(ServiceAreaStatus.ACTIVE.canTransitionTo(ServiceAreaStatus.INACTIVE)).isTrue();
        assertThat(ServiceAreaStatus.INACTIVE.canTransitionTo(ServiceAreaStatus.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("任何非归档状态都可以归档")
    void anyNonArchivedCanArchive() {
        assertThat(ServiceAreaStatus.DRAFT.canTransitionTo(ServiceAreaStatus.ARCHIVED)).isTrue();
        assertThat(ServiceAreaStatus.ACTIVE.canTransitionTo(ServiceAreaStatus.ARCHIVED)).isTrue();
        assertThat(ServiceAreaStatus.INACTIVE.canTransitionTo(ServiceAreaStatus.ARCHIVED)).isTrue();
    }

    @Test
    @DisplayName("归档是终态")
    void archivedIsTerminal() {
        assertThat(ServiceAreaStatus.ARCHIVED.canTransitionTo(ServiceAreaStatus.ACTIVE)).isFalse();
        assertThat(ServiceAreaStatus.ARCHIVED.canTransitionTo(ServiceAreaStatus.INACTIVE)).isFalse();
    }
}
