package com.housekeeping.platform.notification.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模板渲染纯函数测试：变量替换、敏感变量拦截、缺变量失败（docs/modules/12 第 8 节）。
 */
class TemplateRendererTest {

    @Test
    void shouldSubstituteAllVariables() {
        Map<String, Object> vars = Map.of(
                "orderNo", "BKG20261023000001",
                "serviceDate", "2026-10-28",
                "startTime", "09:00:00");
        TemplateRenderer.RenderResult result = TemplateRenderer.render(
                "支付成功",
                "订单 {orderNo} 支付成功，服务安排在 {serviceDate} {startTime}。",
                vars);
        assertThat(result.ok()).isTrue();
        assertThat(result.content())
                .isEqualTo("订单 BKG20261023000001 支付成功，服务安排在 2026-10-28 09:00:00。");
    }

    @Test
    void shouldKeepLiteralTextWithoutPlaceholders() {
        TemplateRenderer.RenderResult result = TemplateRenderer.render(
                "订单已取消", "订单 {orderNo} 已取消，如有疑问请联系客服。",
                Map.of("orderNo", "BKG1"));
        assertThat(result.ok()).isTrue();
        assertThat(result.title()).isEqualTo("订单已取消");
    }

    @Test
    void shouldFailWhenVariableMissing() {
        TemplateRenderer.RenderResult result = TemplateRenderer.render(
                "支付成功", "订单 {orderNo} 支付成功，服务安排在 {serviceDate}。",
                Map.of("orderNo", "BKG1"));
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("serviceDate");
    }

    @Test
    void shouldFailWhenReferencedVariableIsNull() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("orderNo", null);
        TemplateRenderer.RenderResult result = TemplateRenderer.render(
                "标题", "订单 {orderNo}", vars);
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("orderNo");
    }

    @Test
    void shouldBlockSensitiveVariableInTitle() {
        TemplateRenderer.RenderResult result = TemplateRenderer.render(
                "您的订单 {contactPhone} 已受理",
                "正文 {orderNo}",
                Map.of("contactPhone", "13800138000", "orderNo", "BKG1"));
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("contactPhone");
    }

    @Test
    void shouldBlockSensitiveVariableInContent() {
        TemplateRenderer.RenderResult result = TemplateRenderer.render(
                "标题", "地址：{fullAddress}",
                Map.of("fullAddress", "广州市天河区五山路 1 号 101"));
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("fullAddress");
    }

    @Test
    void shouldFindForbiddenVariableForTemplateSave() {
        assertThat(TemplateRenderer.findForbiddenVariable("手机号 {mobile}")).isEqualTo("mobile");
        assertThat(TemplateRenderer.findForbiddenVariable("订单 {orderNo}")).isNull();
    }

    @Test
    void shouldRenderEmptyVariableMapWithoutPlaceholders() {
        TemplateRenderer.RenderResult result = TemplateRenderer.render("标题", "正文无变量", null);
        assertThat(result.ok()).isTrue();
        assertThat(result.content()).isEqualTo("正文无变量");
    }
}
