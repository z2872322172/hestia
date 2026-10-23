package com.housekeeping.platform.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 角色组合规则测试。
 */
class RoleCombinationPolicyTest {

    @Test
    @DisplayName("外部角色之间可以组合")
    void allowsExternalCombination() {
        assertThat(RoleCombinationPolicy.isAllowed(List.of(RoleCode.CUSTOMER))).isTrue();
        assertThat(RoleCombinationPolicy.isAllowed(List.of(RoleCode.WORKER))).isTrue();
        assertThat(RoleCombinationPolicy.isAllowed(List.of(RoleCode.CUSTOMER, RoleCode.WORKER))).isTrue();
    }

    @Test
    @DisplayName("内部角色单独使用合法")
    void allowsInternalAlone() {
        assertThat(RoleCombinationPolicy.isAllowed(List.of(RoleCode.OPERATOR))).isTrue();
        assertThat(RoleCombinationPolicy.isAllowed(List.of(RoleCode.ADMIN))).isTrue();
    }

    @Test
    @DisplayName("内部与外部混用被拒绝")
    void rejectsMixedCombination() {
        assertThat(RoleCombinationPolicy.isAllowed(List.of(RoleCode.CUSTOMER, RoleCode.ADMIN))).isFalse();
        assertThat(RoleCombinationPolicy.isAllowed(List.of(RoleCode.WORKER, RoleCode.OPERATOR))).isFalse();
        assertThatThrownBy(() -> RoleCombinationPolicy.requireAllowed(List.of(RoleCode.CUSTOMER, RoleCode.ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空角色集合被拒绝")
    void rejectsEmpty() {
        assertThatThrownBy(() -> RoleCombinationPolicy.requireAllowed(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
