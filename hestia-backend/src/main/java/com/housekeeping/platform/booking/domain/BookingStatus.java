package com.housekeeping.platform.booking.domain;

/**
 * 订单主状态机（docs/modules/07 第 6.1、6.3 节）。
 *
 * <p>合法迁移矩阵：</p>
 *
 * <pre>
 * PENDING_PAYMENT -&gt; CONFIRMED / CANCELED
 * CONFIRMED       -&gt; EN_ROUTE / CANCELED
 * EN_ROUTE        -&gt; IN_SERVICE / CANCELED（运营异常处理）
 * IN_SERVICE      -&gt; PENDING_CONFIRM（不可取消，走售后）
 * PENDING_CONFIRM -&gt; COMPLETED
 * COMPLETED / CANCELED -&gt; 终态
 * </pre>
 *
 * <p>与 {@code V5 chk_booking_order_status} 的 CHECK 约束取值一一对应。
 * 落库一律条件更新（{@code WHERE order_status = 期望值}），本枚举只做合法性预判；
 * 并发竞争由数据库条件更新兜底（docs 第 17 节）。</p>
 */
public enum BookingStatus {

    /** 已创建并占用时段，等待支付。 */
    PENDING_PAYMENT {

        @Override
        public boolean canTransitTo(BookingStatus target) {
            return target == CONFIRMED || target == CANCELED;
        }
    },
    /** 已支付，订单有效。 */
    CONFIRMED {

        @Override
        public boolean canTransitTo(BookingStatus target) {
            return target == EN_ROUTE || target == CANCELED;
        }
    },
    /** 服务人员已出发。 */
    EN_ROUTE {

        @Override
        public boolean canTransitTo(BookingStatus target) {
            return target == IN_SERVICE || target == CANCELED;
        }
    },
    /** 服务进行中。 */
    IN_SERVICE {

        @Override
        public boolean canTransitTo(BookingStatus target) {
            // IN_SERVICE 不能直接取消，只能进入售后（docs 第 6.3 节）
            return target == PENDING_CONFIRM;
        }
    },
    /** 服务人员已申请完成，等待客户确认或系统自动确认。 */
    PENDING_CONFIRM {

        @Override
        public boolean canTransitTo(BookingStatus target) {
            return target == COMPLETED;
        }
    },
    /** 客户确认或系统自动确认，终态。 */
    COMPLETED {

        @Override
        public boolean canTransitTo(BookingStatus target) {
            return false;
        }
    },
    /** 订单取消，终态。 */
    CANCELED {

        @Override
        public boolean canTransitTo(BookingStatus target) {
            return false;
        }
    };

    /**
     * 判断能否迁移到目标状态。
     *
     * @param target 目标状态
     * @return 合法迁移返回 {@code true}
     */
    public abstract boolean canTransitTo(BookingStatus target);

    /**
     * 当前状态是否允许客户取消：
     * 待支付可随时取消；已支付在服务开始前可取消（走费用计算）；
     * {@code EN_ROUTE} 及以后进入售后，不再走取消链路。
     *
     * @return 允许取消返回 {@code true}
     */
    public boolean cancellable() {
        return this == PENDING_PAYMENT || this == CONFIRMED;
    }

    /**
     * 当前状态是否允许改期（仅已支付且未出发）。
     *
     * @return 允许改期返回 {@code true}
     */
    public boolean reschedulable() {
        return this == CONFIRMED;
    }

    /**
     * 当前状态是否已支付（取消时需要计算费用与退款）。
     *
     * @return 已支付返回 {@code true}
     */
    public boolean paid() {
        return this == CONFIRMED || this == EN_ROUTE || this == IN_SERVICE
                || this == PENDING_CONFIRM || this == COMPLETED;
    }

    /**
     * 解析订单状态字符串。
     *
     * @param value 数据库存量值
     * @return 订单状态
     */
    public static BookingStatus of(String value) {
        return BookingStatus.valueOf(value);
    }
}
