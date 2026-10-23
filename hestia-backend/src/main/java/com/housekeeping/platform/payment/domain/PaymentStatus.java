package com.housekeeping.platform.payment.domain;

/**
 * 支付记录状态机（docs/modules/10 第 5.1 节）。
 *
 * <pre>
 * CREATED -&gt; PAYING -&gt; SUCCESS
 * CREATED -&gt; PAYING -&gt; FAILED
 * CREATED -&gt; CLOSED
 * PAYING  -&gt; CLOSED
 * FAILED  -&gt; CLOSED
 * </pre>
 *
 * <p>{@code SUCCESS} 为终态，不允许回滚为未支付；与
 * {@code V6 chk_payment_record_status} 的 CHECK 约束取值一一对应。
 * 落库一律条件更新（{@code WHERE status IN (...)})，本枚举只做合法性预判。</p>
 */
public enum PaymentStatus {

    /** 支付单已创建。 */
    CREATED {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return target == PAYING || target == CLOSED;
        }
    },
    /** 已向支付渠道发起支付。 */
    PAYING {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return target == SUCCESS || target == FAILED || target == CLOSED;
        }
    },
    /** 支付成功（终态）。 */
    SUCCESS {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return false;
        }
    },
    /** 支付失败。 */
    FAILED {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return target == CLOSED;
        }
    },
    /** 支付单关闭（终态，支付超时 / 订单取消）。 */
    CLOSED {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return false;
        }
    };

    /**
     * 判断能否迁移到目标状态。
     *
     * @param target 目标状态
     * @return 合法迁移返回 {@code true}
     */
    public abstract boolean canTransitTo(PaymentStatus target);
}
