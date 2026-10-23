package com.housekeeping.platform.catalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务项目状态机测试（docs/modules/04 第 6.1 节）。
 */
class ServiceItemStatusTest {

    @Test
    @DisplayName("只有三条合法迁移边")
    void allowsOnlyDocumentedTransitions() {
        assertThat(ServiceItemStatus.DRAFT.canTransitionTo(ServiceItemStatus.PUBLISHED)).isTrue();
        assertThat(ServiceItemStatus.PUBLISHED.canTransitionTo(ServiceItemStatus.OFFLINE)).isTrue();
        assertThat(ServiceItemStatus.OFFLINE.canTransitionTo(ServiceItemStatus.PUBLISHED)).isTrue();

        // 草稿没上过架，"下架"没有意义
        assertThat(ServiceItemStatus.DRAFT.canTransitionTo(ServiceItemStatus.OFFLINE)).isFalse();
        // 同状态重复迁移一律拒绝，避免"重复发布"把 published_at 刷成新时间
        assertThat(ServiceItemStatus.DRAFT.canTransitionTo(ServiceItemStatus.DRAFT)).isFalse();
        assertThat(ServiceItemStatus.PUBLISHED.canTransitionTo(ServiceItemStatus.PUBLISHED)).isFalse();
        assertThat(ServiceItemStatus.OFFLINE.canTransitionTo(ServiceItemStatus.OFFLINE)).isFalse();
    }

    @Test
    @DisplayName("可迁移目标集合")
    void reportsAllowedTargets() {
        assertThat(ServiceItemStatus.DRAFT.allowedTargets())
                .containsExactly(ServiceItemStatus.PUBLISHED);
        assertThat(ServiceItemStatus.PUBLISHED.allowedTargets())
                .containsExactly(ServiceItemStatus.OFFLINE);
        assertThat(ServiceItemStatus.OFFLINE.allowedTargets())
                .containsExactly(ServiceItemStatus.PUBLISHED);
    }

    @Test
    @DisplayName("只有 PUBLISHED 可售")
    void onlyPublishedIsSellable() {
        assertThat(ServiceItemStatus.PUBLISHED.isSellable()).isTrue();
        assertThat(ServiceItemStatus.DRAFT.isSellable()).isFalse();
        assertThat(ServiceItemStatus.OFFLINE.isSellable()).isFalse();
    }

    @Test
    @DisplayName("枚举值数量固定为三个，新增状态必须同步状态机")
    void hasExactlyThreeStates() {
        assertThat(EnumSet.allOf(ServiceItemStatus.class))
                .containsExactlyInAnyOrder(ServiceItemStatus.DRAFT,
                        ServiceItemStatus.PUBLISHED, ServiceItemStatus.OFFLINE);
    }
}
