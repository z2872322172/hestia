package com.housekeeping.platform.scheduling.domain;

/**
 * 人员时段状态机（docs/modules/06 第 6.2、7 节）。
 *
 * <p>合法迁移：</p>
 *
 * <pre>
 * AVAILABLE -&gt; HELD -&gt; BOOKED
 * HELD      -&gt; AVAILABLE / CLOSED
 * BOOKED    -&gt; AVAILABLE / CLOSED
 * AVAILABLE -&gt; BLOCKED
 * BLOCKED   -&gt; AVAILABLE
 * </pre>
 *
 * <p>不变量（与 V4 的 {@code chk_worker_slot_order_binding} 数据库约束一一对应，
 * Java 侧先挡一轮，数据库兜底并发与脏数据）：</p>
 * <ul>
 *   <li>{@code AVAILABLE} 不允许关联订单与锁定到期时间；</li>
 *   <li>{@code HELD} 必须关联订单且带锁定到期时间；</li>
 *   <li>{@code BOOKED} 必须关联订单且不带锁定到期时间。</li>
 * </ul>
 *
 * <p>所有状态变更都通过条件更新（{@code WHERE status = 期望值}）落库，
 * 两个并发请求竞争同一时段只有一个成功——这个枚举只负责"这步迁移合不合法"，
 * 并发正确性在 {@code WorkerSlotMapper} 的 SQL 里。</p>
 */
public enum SlotStatus {

    /** 可被新订单占用。 */
    AVAILABLE {

        @Override
        public boolean canTransitTo(SlotStatus target) {
            return target == HELD || target == BLOCKED || target == CLOSED;
        }
    },
    /** 已选人员并等待支付。 */
    HELD {

        @Override
        public boolean canTransitTo(SlotStatus target) {
            return target == BOOKED || target == AVAILABLE || target == CLOSED;
        }
    },
    /** 已支付，正式占用。 */
    BOOKED {

        @Override
        public boolean canTransitTo(SlotStatus target) {
            // BOOKED 不允许直接变 BLOCKED：请假/停排遇到已支付订单必须走改派（docs 12.2）
            return target == AVAILABLE || target == CLOSED;
        }
    },
    /** 请假、培训或人工停用。 */
    BLOCKED {

        @Override
        public boolean canTransitTo(SlotStatus target) {
            return target == AVAILABLE;
        }
    },
    /** 日期已过或排班关闭，终态。 */
    CLOSED {

        @Override
        public boolean canTransitTo(SlotStatus target) {
            return false;
        }
    };

    /**
     * 判断能否迁移到目标状态。
     *
     * @param target 目标状态
     * @return 合法迁移返回 {@code true}
     */
    public abstract boolean canTransitTo(SlotStatus target);

    /**
     * 该状态是否占用着订单（HELD 与 BOOKED 都关联订单）。
     *
     * @return 占用返回 {@code true}
     */
    public boolean occupiedByOrder() {
        return this == HELD || this == BOOKED;
    }

    /**
     * 解析时段状态字符串。
     *
     * @param value 数据库存量值
     * @return 时段状态
     */
    public static SlotStatus of(String value) {
        return SlotStatus.valueOf(value);
    }
}
