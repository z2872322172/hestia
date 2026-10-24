package com.housekeeping.platform.bootstrap.web;

import com.housekeeping.platform.booking.application.BookingPaymentTimeoutJob;
import com.housekeeping.platform.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单链路冒烟接口：超时扫描触发。
 *
 * <p>只在 {@code app.security.smoke-api-enabled=true} 的 profile 下可访问（local / demo / test），
 * 生产环境由安全配置统一 denyAll。支付成功不再走冒烟端点——改用
 * {@code POST /api/v1/payment/callback/mock} 真实回调（含验签与幂等占位）。</p>
 */
@RestController
@RequestMapping("/api/v1/system/smoke/booking")
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "系统冒烟-订单", description = "支付超时扫描触发")
public class SystemSmokeBookingController {

    private final BookingPaymentTimeoutJob timeoutJob;

    public SystemSmokeBookingController(BookingPaymentTimeoutJob timeoutJob) {
        this.timeoutJob = timeoutJob;
    }

    /**
     * 手动触发一轮支付超时扫描。
     *
     * @param asOf 可选的模拟当前时刻（ISO 格式）；缺省用真实时钟，
     *             冒烟脚本用它把刚下的订单视作已超时
     * @return 本轮处理的订单数
     */
    @PostMapping("/timeout-sweep")
    @Operation(summary = "触发支付超时扫描", description = "执行 BookingPaymentTimeoutJob.runOnce，可注入模拟时钟")
    public Result<Map<String, Integer>> timeoutSweep(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        return Result.ok(Map.of("handled",
                asOf == null ? timeoutJob.runOnce() : timeoutJob.runOnce(asOf)));
    }
}
