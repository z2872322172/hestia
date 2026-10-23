package com.housekeeping.platform.location.domain;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 服务区域排序与冲突判定（docs/modules/03 第 7.3 节）。
 *
 * <p>排序键依次为：</p>
 * <ol>
 *   <li>覆盖级别（街道优先于区县）；</li>
 *   <li>{@code service_area.priority} 降序；</li>
 *   <li>{@code effective_from} 降序（新配置优先）。</li>
 * </ol>
 *
 * <p>三个键<b>全部相同</b>才算冲突。少判一个键的后果很实际：
 * 只看优先级就会把"街道覆盖 priority=50"和"区县覆盖 priority=100"判成冲突，
 * 而文档明确要求街道优先——运营配了两层覆盖反而匹配不了。</p>
 *
 * <p>这里是纯函数，不依赖数据库与 Spring，便于单测穷举排序与冲突组合。</p>
 */
public final class MatchRanking {

    /**
     * 排序规则。
     *
     * <p>{@code effectiveFrom} 为 {@code null} 时按 {@link LocalDateTime#MIN} 处理，
     * 表示"从一开始就生效"，因此在降序里排在最后。</p>
     */
    public static final Comparator<MatchCandidate> ORDER = Comparator
            .comparingInt((MatchCandidate candidate) -> candidate.coverageLevel().rank())
            .thenComparing(Comparator.comparingInt(MatchCandidate::priority).reversed())
            .thenComparing(Comparator.comparing(MatchRanking::effectiveFromOf).reversed());

    private MatchRanking() {
    }

    /**
     * 按规则排序。
     *
     * @param candidates 候选列表
     * @return 排序后的新列表（不修改入参）
     */
    public static List<MatchCandidate> rank(List<MatchCandidate> candidates) {
        return candidates.stream().sorted(ORDER).toList();
    }

    /**
     * 判定排序后的前两名是否构成冲突。
     *
     * @param ranked 已排序的候选列表
     * @return 三个排序键完全相同但服务区域不同时返回 {@code true}
     */
    public static boolean isAmbiguous(List<MatchCandidate> ranked) {
        if (ranked.size() < 2) {
            return false;
        }
        MatchCandidate first = ranked.get(0);
        MatchCandidate second = ranked.get(1);
        return first.coverageLevel() == second.coverageLevel()
                && first.priority() == second.priority()
                && effectiveFromOf(first).equals(effectiveFromOf(second))
                && first.serviceAreaId() != second.serviceAreaId();
    }

    private static LocalDateTime effectiveFromOf(MatchCandidate candidate) {
        return candidate.effectiveFrom() == null ? LocalDateTime.MIN : candidate.effectiveFrom();
    }
}
