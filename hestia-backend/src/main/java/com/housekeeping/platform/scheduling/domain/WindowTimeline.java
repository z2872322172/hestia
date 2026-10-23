package com.housekeeping.platform.scheduling.domain;

import java.time.LocalTime;
import java.util.List;

/**
 * 时间窗时间轴纯函数（docs/modules/06 第 5 节）。
 *
 * <p>与 catalog 的 {@code PriceCalculator} 同一思路：重叠校验、时长匹配这些规则
 * 全是静态方法，不注入 Spring、不碰数据库，可以用穷举单测把边界钉死。
 * 服务时间窗在同一天内不能重叠，服务项目时长必须能放进目标时间窗，
 * 两条规则都只在时间维度上运算，不需要任何外部状态。</p>
 */
public final class WindowTimeline {

    private WindowTimeline() {
    }

    /**
     * 判断单个时间区间是否合法。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 结束晚于开始返回 {@code true}（暂不支持跨零点，首版时间窗都在白天）
     */
    public static boolean validSpan(LocalTime start, LocalTime end) {
        return start != null && end != null && end.isAfter(start);
    }

    /**
     * 判断两个时间区间是否重叠（左闭右开：12:00 结束与 12:00 开始不算重叠）。
     *
     * @param startA A 区间开始
     * @param endA   A 区间结束
     * @param startB B 区间开始
     * @param endB   B 区间结束
     * @return 重叠返回 {@code true}
     */
    public static boolean overlaps(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    /**
     * 校验一批时间区间互不重叠，用于新建/修改时间窗时的整体校验。
     *
     * <p>注意只对同一服务日的窗口有意义；数据库不做这个约束，
     * 因为"同一天"是业务概念，校验放在创建时间窗的入口处。</p>
     *
     * @param spans 时间区间列表（start, end 成对）
     * @return 全部互不重叠返回 {@code true}
     */
    public static boolean allNonOverlapping(List<LocalTime[]> spans) {
        for (int i = 0; i < spans.size(); i++) {
            for (int j = i + 1; j < spans.size(); j++) {
                LocalTime[] a = spans.get(i);
                LocalTime[] b = spans.get(j);
                if (overlaps(a[0], a[1], b[0], b[1])) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 判断服务时长能否放入时间窗。
     *
     * @param durationMinutes 服务预计时长（分钟），必须为正
     * @param start           时间窗开始
     * @param end             时间窗结束
     * @return 能放入返回 {@code true}
     */
    public static boolean fits(int durationMinutes, LocalTime start, LocalTime end) {
        if (durationMinutes <= 0 || !validSpan(start, end)) {
            return false;
        }
        long windowMinutes = java.time.Duration.between(start, end).toMinutes();
        return durationMinutes <= windowMinutes;
    }

    /**
     * 计算时间窗长度（分钟）。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 分钟数
     */
    public static long minutesOf(LocalTime start, LocalTime end) {
        return java.time.Duration.between(start, end).toMinutes();
    }
}
