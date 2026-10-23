package com.housekeeping.platform.customer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地址脱敏规则测试。
 *
 * <p>脱敏是隐私红线：一旦在服务端漏做，前端再怎么隐藏都能被抓包看到。</p>
 */
class AddressMaskingTest {

    @Test
    @DisplayName("姓名只保留首字")
    void masksName() {
        assertThat(AddressMasking.maskName("李女士")).isEqualTo("李**");
        assertThat(AddressMasking.maskName("王")).isEqualTo("*");
        assertThat(AddressMasking.maskName(null)).isEmpty();
    }

    @Test
    @DisplayName("电话保留前三后四")
    void masksPhone() {
        assertThat(AddressMasking.maskPhone("13800138000")).isEqualTo("138****8000");
        assertThat(AddressMasking.maskPhone("123")).isEqualTo("****");
    }

    @Test
    @DisplayName("地址只到区县与小区级别")
    void masksAddress() {
        assertThat(AddressMasking.maskAddress("天河区", "示例花园")).isEqualTo("天河区示例花园");
    }
}
