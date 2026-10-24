package com.housekeeping.platform.aftersales.api;

import com.housekeeping.platform.aftersales.application.ReviewApplicationService;
import com.housekeeping.platform.aftersales.application.view.ReviewView;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 客户评价接口（docs/modules/11 第 5 节）。
 */
@RestController
@RequestMapping("/api/v1/customer")
@Validated
@RequiresPersistence
@Tag(name = "客户-评价", description = "提交订单评价、查看我的评价")
public class CustomerReviewController {

    private final ReviewApplicationService reviewService;

    private final CurrentAccountResolver currentAccountResolver;

    public CustomerReviewController(ReviewApplicationService reviewService,
                                    CurrentAccountResolver currentAccountResolver) {
        this.reviewService = reviewService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 提交评价（仅已完成订单，一单一评价）。
     *
     * @param orderNo 订单号
     * @param request 评价内容
     * @return 评价视图
     */
    @PostMapping("/bookings/{orderNo}/reviews")
    @Operation(summary = "提交评价", description = "订单完成后可评；必须覆盖全部启用维度，各维度 0-5 分")
    public Result<ReviewView> submit(@PathVariable String orderNo,
                                     @Valid @RequestBody SubmitReviewRequest request) {
        return Result.ok(reviewService.submit(currentAccountResolver.requireAccountId(),
                orderNo, request.dimensionScores(), request.content(), request.fileIds()));
    }

    /**
     * 我的评价列表。
     *
     * @param page 页码
     * @param size 条数
     * @return 分页评价
     */
    @GetMapping("/reviews")
    @Operation(summary = "我的评价")
    public Result<PageResult<ReviewView>> myReviews(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.myReviews(currentAccountResolver.requireAccountId(),
                page, size));
    }

    /** 提交评价请求体。 */
    public record SubmitReviewRequest(
            @NotEmpty(message = "维度评分不能为空") Map<String, BigDecimal> dimensionScores,
            String content,
            List<Long> fileIds) {
    }
}
