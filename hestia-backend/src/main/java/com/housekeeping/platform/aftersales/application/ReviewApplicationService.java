package com.housekeeping.platform.aftersales.application;

import com.housekeeping.platform.aftersales.application.view.ReviewView;
import com.housekeeping.platform.aftersales.persistence.entity.ReviewDimensionEntity;
import com.housekeeping.platform.aftersales.persistence.entity.ReviewDimensionScoreEntity;
import com.housekeeping.platform.aftersales.persistence.entity.ReviewEvidenceEntity;
import com.housekeeping.platform.aftersales.persistence.entity.ServiceReviewEntity;
import com.housekeeping.platform.aftersales.persistence.mapper.ReviewDimensionMapper;
import com.housekeeping.platform.aftersales.persistence.mapper.ReviewDimensionScoreMapper;
import com.housekeeping.platform.aftersales.persistence.mapper.ReviewEvidenceMapper;
import com.housekeeping.platform.aftersales.persistence.mapper.ServiceReviewMapper;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.application.CustomerProfileService;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评价应用服务（docs/modules/11 第 5 节）。
 *
 * <p>规则：仅已完成订单可评价、一单一条评价（uk booking_id 兜底并发）、
 * 评价人必须是订单客户、提交后不可修改只能由运营隐藏。
 * 综合分 = 各维度简单平均（首版规则）。评价提交后立即重算人员质量汇总。</p>
 */
@Service
@RequiresPersistence
public class ReviewApplicationService {

    private static final String SEQ_REVIEW = "REVIEW";

    private static final String PREFIX_REVIEW = "RVS";

    private final ServiceReviewMapper reviewMapper;

    private final ReviewDimensionScoreMapper scoreMapper;

    private final ReviewDimensionMapper dimensionMapper;

    private final ReviewEvidenceMapper evidenceMapper;

    private final BookingOrderMapper orderMapper;

    private final CustomerProfileService customerProfileService;

    private final WorkerQualityService workerQualityService;

    private final BusinessNoGenerator noGenerator;

    public ReviewApplicationService(ServiceReviewMapper reviewMapper,
                                    ReviewDimensionScoreMapper scoreMapper,
                                    ReviewDimensionMapper dimensionMapper,
                                    ReviewEvidenceMapper evidenceMapper,
                                    BookingOrderMapper orderMapper,
                                    CustomerProfileService customerProfileService,
                                    WorkerQualityService workerQualityService,
                                    BusinessNoGenerator noGenerator) {
        this.reviewMapper = reviewMapper;
        this.scoreMapper = scoreMapper;
        this.dimensionMapper = dimensionMapper;
        this.evidenceMapper = evidenceMapper;
        this.orderMapper = orderMapper;
        this.customerProfileService = customerProfileService;
        this.workerQualityService = workerQualityService;
        this.noGenerator = noGenerator;
    }

    /**
     * 提交评价（docs/modules/11 第 5.3 节）。
     *
     * @param customerAccountId 客户账号 ID
     * @param orderNo           订单号
     * @param dimensionScores   维度分数（必须覆盖全部启用维度）
     * @param content           评价内容（可空）
     * @param fileIds           图片附件（可空）
     * @return 评价视图
     */
    @Transactional
    public ReviewView submit(long customerAccountId, String orderNo,
                             Map<String, BigDecimal> dimensionScores,
                             String content, List<Long> fileIds) {
        BookingOrderEntity order = requireOwnedOrder(customerAccountId, orderNo);
        if (!"COMPLETED".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (reviewMapper.selectByBookingId(order.getId()) != null) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        List<ReviewDimensionEntity> dimensions = dimensionMapper.selectActive();
        if (dimensions.isEmpty()) {
            throw new BusinessException(ErrorCode.REVIEW_DIMENSION_INVALID, "评价维度未配置");
        }
        Map<String, BigDecimal> submitted = dimensionScores == null
                ? Map.of() : new LinkedHashMap<>(dimensionScores);
        BigDecimal total = BigDecimal.ZERO;
        for (ReviewDimensionEntity dimension : dimensions) {
            BigDecimal score = submitted.remove(dimension.getDimensionCode());
            if (score == null) {
                throw new BusinessException(ErrorCode.REVIEW_DIMENSION_INVALID,
                        "缺少维度：" + dimension.getDimensionCode());
            }
            if (score.compareTo(BigDecimal.ZERO) <= 0
                    || score.compareTo(dimension.getMaxScore()) > 0) {
                throw new BusinessException(ErrorCode.REVIEW_SCORE_INVALID,
                        dimension.getDimensionCode() + " 评分须在 0 到 "
                                + dimension.getMaxScore() + " 之间");
            }
            total = total.add(score);
        }
        if (!submitted.isEmpty()) {
            throw new BusinessException(ErrorCode.REVIEW_DIMENSION_INVALID,
                    "包含未启用的维度：" + String.join(",", submitted.keySet()));
        }
        BigDecimal overall = total.divide(BigDecimal.valueOf(dimensions.size()), 2,
                RoundingMode.HALF_UP);

        ServiceReviewEntity review = new ServiceReviewEntity();
        review.setReviewNo(noGenerator.next(SEQ_REVIEW, PREFIX_REVIEW));
        review.setBookingId(order.getId());
        review.setCustomerId(order.getCustomerId());
        review.setWorkerId(order.getCurrentWorkerId());
        review.setOverallScore(overall);
        review.setContent(content);
        review.setStatus("VISIBLE");
        review.setCreatedByAccountId(customerAccountId);
        try {
            reviewMapper.insert(review);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            // 并发双击：uk_service_review_booking 兜底
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        List<ReviewDimensionScoreEntity> scoreRows = new ArrayList<>();
        for (ReviewDimensionEntity dimension : dimensions) {
            ReviewDimensionScoreEntity score = new ReviewDimensionScoreEntity();
            score.setReviewId(review.getId());
            score.setDimensionCode(dimension.getDimensionCode());
            // 维度分从原始入参取值（submitted 副本已在白名单校验中逐项移除）
            score.setScore(dimensionScores.get(dimension.getDimensionCode()));
            scoreRows.add(score);
        }
        scoreRows.forEach(scoreMapper::insert);

        if (fileIds != null && !fileIds.isEmpty()) {
            for (int i = 0; i < fileIds.size(); i++) {
                Long fileId = fileIds.get(i);
                ReviewEvidenceEntity existing = evidenceMapper.selectByFileId(fileId);
                if (existing != null) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "文件 " + fileId + " 已被其他评价使用");
                }
                ReviewEvidenceEntity evidence = new ReviewEvidenceEntity();
                evidence.setReviewId(review.getId());
                evidence.setFileId(fileId);
                evidence.setSortOrder(i);
                evidenceMapper.insert(evidence);
            }
        }

        // 订单评价摘要（NOT_REVIEWED → REVIEWED）
        BookingOrderEntity patch = new BookingOrderEntity();
        patch.setId(order.getId());
        patch.setReviewStatus("REVIEWED");
        orderMapper.updateById(patch);

        if (order.getCurrentWorkerId() != null) {
            workerQualityService.recalculate(order.getCurrentWorkerId());
        }
        return ReviewView.from(review, scoreMapper.selectByReviewId(review.getId()),
                evidenceMapper.selectByReviewId(review.getId()));
    }

    /**
     * 客户评价列表（本人可见，含被隐藏的）。
     *
     * @param customerAccountId 客户账号 ID
     * @param page              页码
     * @param size              条数
     * @return 分页评价
     */
    @Transactional(readOnly = true)
    public PageResult<ReviewView> myReviews(long customerAccountId, int page, int size) {
        long customerId = customerProfileService.requireUsable(customerAccountId).customerId();
        List<ServiceReviewEntity> records =
                reviewMapper.selectPageByCustomer(customerId, (page - 1) * size, size);
        long total = reviewMapper.countByCustomer(customerId);
        List<ReviewView> views = records.stream()
                .map(review -> ReviewView.from(review,
                        scoreMapper.selectByReviewId(review.getId()),
                        evidenceMapper.selectByReviewId(review.getId())))
                .toList();
        return PageResult.of(views, page, size, total);
    }

    /**
     * 运营隐藏违规评价（VISIBLE → HIDDEN，保留原始记录与审计）。
     *
     * @param operatorAccountId 运营账号 ID
     * @param reviewNo          评价单号
     * @param reason            隐藏原因
     */
    @Transactional
    public void hide(long operatorAccountId, String reviewNo, String reason) {
        if (reviewMapper.hide(reviewNo, reason, operatorAccountId) == 0) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
    }

    /**
     * 校验订单归属（越权一律 404，不泄露存在性）。
     */
    private BookingOrderEntity requireOwnedOrder(long customerAccountId, String orderNo) {
        BookingOrderEntity order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        if (customerProfileService.requireUsable(customerAccountId).customerId()
                != order.getCustomerId()) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OWNED);
        }
        return order;
    }
}
