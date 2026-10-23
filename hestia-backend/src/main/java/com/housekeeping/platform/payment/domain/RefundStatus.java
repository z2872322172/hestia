package com.housekeeping.platform.payment.domain;

/**
 * 退款记录状态机（docs/modules/10 第 6 节）。
 *
 * <pre>
 * REQUESTED  -&gt; APPROVED -&gt; PROCESSING -&gt; SUCCESS
 * REQUESTED  -&gt; REJECTED
 * APPROVED   -&gt; FAILED
 * PROCESSING -&gt; FAILED
 * FAILED     -&gt; PROCESSING（重试）
 * REQUESTED  -&gt; CANCELED
 * </pre>
 *
 * <p>{@code SUCCESS} 为终态。审批与执行分开记录：审批置 {@code APPROVED}，
 * 网关调用置 {@code PROCESSING}，外部退款失败不能伪装成成功（docs 第 2.12 条）。</p>
 */
public enum RefundStatus {

    /** 已申请，等待运营审批。 */
    REQUESTED {
        @Override
        public boolean canTransitTo(RefundStatus target) {
            return target == APPROVED || target == REJECTED || target == CANCELED;
        }
    },
    /** 运营审批通过，等待执行。 */
    APPROVED {
        @Override
        public boolean canTransitTo(RefundStatus target) {
            return target == PROCESSING || target == FAILED;
        }
    },
    /** 已提交网关执行中。 */
    PROCESSING {
        @Override
        public boolean canTransitTo(RefundStatus target) {
            return target == SUCCESS || target == FAILED;
        }
    },
    /** 退款成功（终态）。 */
    SUCCESS {
        @Override
        public boolean canTransitTo(RefundStatus target) {
            return false;
        }
    },
    /** 运营拒绝（终态）。 */
    REJECTED {
        @Override
        public boolean canTransitTo(RefundStatus target) {
            return false;
        }
    },
    /** 执行失败，可重试回 PROCESSING。 */
    FAILED {
        @Override
        public boolean canTransitTo(RefundStatus target) {
            return target == PROCESSING;
        }
    },
    /** 申请人撤销（终态）。 */
    CANCELED {
        @Override
        public boolean canTransitTo(RefundStatus target) {
            return false;
        }
    };

    /**
     * 判断能否迁移到目标状态。
     *
     * @param target 目标状态
     * @return 合法迁移返回 {@code true}
     */
    public abstract boolean canTransitTo(RefundStatus target);
}
