package com.housekeeping.platform.location.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务区域匹配排序与冲突判定测试。
 *
 * <p>这些用例对应 docs/modules/03 第 7.4 节的示例：
 * 街道覆盖必须压过优先级更高的区县覆盖；三个排序键全同才算冲突。</p>
 */
class MatchRankingTest {

    private static final LocalDateTime EARLY = LocalDateTime.of(2026, 1, 1, 0, 0);

    private static final LocalDateTime LATE = LocalDateTime.of(2026, 6, 1, 0, 0);

    @Test
    @DisplayName("街道覆盖优先于区县覆盖，即使区县优先级更高")
    void streetCoverageWinsOverHigherPriorityDistrict() {
        MatchCandidate district = new MatchCandidate(1L, "SA1", "天河区核心片区",
                CoverageLevel.DISTRICT, 100, EARLY, "440106");
        MatchCandidate street = new MatchCandidate(2L, "SA2", "天河区五山片区",
                CoverageLevel.STREET, 50, EARLY, "440106001");

        List<MatchCandidate> ranked = MatchRanking.rank(List.of(district, street));

        assertThat(ranked.get(0).serviceAreaId()).isEqualTo(2L);
        assertThat(MatchRanking.isAmbiguous(ranked)).isFalse();
    }

    @Test
    @DisplayName("同级别时优先级数值大者优先")
    void higherPriorityWinsWithinSameLevel() {
        MatchCandidate low = new MatchCandidate(1L, "SA1", "A", CoverageLevel.DISTRICT, 10, EARLY, "440106");
        MatchCandidate high = new MatchCandidate(2L, "SA2", "B", CoverageLevel.DISTRICT, 90, EARLY, "440106");

        assertThat(MatchRanking.rank(List.of(low, high)).get(0).serviceAreaId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("同级别同优先级时生效时间晚者优先")
    void laterEffectiveFromWinsWhenPriorityTies() {
        MatchCandidate early = new MatchCandidate(1L, "SA1", "A", CoverageLevel.DISTRICT, 50, EARLY, "440106");
        MatchCandidate late = new MatchCandidate(2L, "SA2", "B", CoverageLevel.DISTRICT, 50, LATE, "440106");

        assertThat(MatchRanking.rank(List.of(early, late)).get(0).serviceAreaId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("三个排序键全同且区域不同判为冲突")
    void sameKeysWithDifferentAreaIsAmbiguous() {
        MatchCandidate first = new MatchCandidate(1L, "SA1", "A", CoverageLevel.STREET, 100, EARLY, "440106001");
        MatchCandidate second = new MatchCandidate(2L, "SA2", "B", CoverageLevel.STREET, 100, EARLY, "440106001");

        assertThat(MatchRanking.isAmbiguous(MatchRanking.rank(List.of(first, second)))).isTrue();
    }

    @Test
    @DisplayName("只有一个候选时不判冲突")
    void singleCandidateIsNotAmbiguous() {
        MatchCandidate only = new MatchCandidate(1L, "SA1", "A", CoverageLevel.DISTRICT, 100, EARLY, "440106");
        assertThat(MatchRanking.isAmbiguous(MatchRanking.rank(List.of(only)))).isFalse();
    }

    @Test
    @DisplayName("生效时间为空视为最早生效，排序时排在有时间的候选之后")
    void nullEffectiveFromRanksLast() {
        MatchCandidate timeless = new MatchCandidate(1L, "SA1", "A", CoverageLevel.DISTRICT, 50, null, "440106");
        MatchCandidate dated = new MatchCandidate(2L, "SA2", "B", CoverageLevel.DISTRICT, 50, EARLY, "440106");

        assertThat(MatchRanking.rank(List.of(timeless, dated)).get(0).serviceAreaId()).isEqualTo(2L);
    }
}
