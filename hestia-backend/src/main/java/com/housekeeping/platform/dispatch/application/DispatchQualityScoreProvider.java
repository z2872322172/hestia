package com.housekeeping.platform.dispatch.application;

/**
 * 派单质量分提供者契约（docs/modules/08 第 7 节「服务质量」分项）。
 *
 * <p>质量分来自服务评价、投诉率、迟到率与返工率的历史汇总（批次 3 售后模块），
 * 派单模块不直接查询评价表，只依赖本契约——避免 dispatch → aftersales 的编译依赖。</p>
 */
public interface DispatchQualityScoreProvider {

    /**
     * 人员服务质量分。
     *
     * @param workerId 人员 ID
     * @return 0-100 的整数分
     */
    int qualityScore(long workerId);
}
