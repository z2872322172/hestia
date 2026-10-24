package com.housekeeping.platform.dispatch.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 派单评分策略（docs/modules/08 第 7 节，纯函数无副作用）。
 *
 * <p>总分 = Σ 分项分数 × 权重 / 100，保留 3 位小数（对应 {@code DECIMAL(6,3)}）。
 * 分项与权重全部显式传入，方便单测覆盖与后续接入真实数据源。</p>
 *
 * <p>同分排序（总分降序 → 当日负载升序 → 最近分配升序 → workerId 升序）
 * 由应用层使用稳定排序实现：候选生成顺序即库存顺序，同分保持原顺序。</p>
 */
public final class DispatchScorePolicy {

    /** 六个分项在快照 JSON 中的键名，与 docs/modules/08 第 13.1 节权重 JSON 一致。 */
    public static final String KEY_AREA = "area_score";

    public static final String KEY_WORKLOAD = "workload_score";

    public static final String KEY_QUALITY = "quality_score";

    public static final String KEY_SKILL = "skill_score";

    public static final String KEY_CONTINUITY = "continuity_score";

    public static final String KEY_PREFERENCE = "preference_score";

    private DispatchScorePolicy() {
    }

    /**
     * 计算总分并输出评分拆解。
     *
     * @param inputs  六个分项分数（0-100）
     * @param weights 六个分项权重（合计应为 100，由策略表配置保证）
     * @return 总分与分项拆解（{@code LinkedHashMap} 保证 JSON 键序稳定）
     */
    public static ScoreResult score(ScoreInputs inputs, ScoreWeights weights) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put(KEY_AREA, inputs.areaScore());
        breakdown.put(KEY_WORKLOAD, inputs.workloadScore());
        breakdown.put(KEY_QUALITY, inputs.qualityScore());
        breakdown.put(KEY_SKILL, inputs.skillScore());
        breakdown.put(KEY_CONTINUITY, inputs.continuityScore());
        breakdown.put(KEY_PREFERENCE, inputs.preferenceScore());

        BigDecimal total = BigDecimal.ZERO;
        total = total.add(BigDecimal.valueOf(inputs.areaScore())
                .multiply(BigDecimal.valueOf(weights.area())));
        total = total.add(BigDecimal.valueOf(inputs.workloadScore())
                .multiply(BigDecimal.valueOf(weights.workload())));
        total = total.add(BigDecimal.valueOf(inputs.qualityScore())
                .multiply(BigDecimal.valueOf(weights.quality())));
        total = total.add(BigDecimal.valueOf(inputs.skillScore())
                .multiply(BigDecimal.valueOf(weights.skill())));
        total = total.add(BigDecimal.valueOf(inputs.continuityScore())
                .multiply(BigDecimal.valueOf(weights.continuity())));
        total = total.add(BigDecimal.valueOf(inputs.preferenceScore())
                .multiply(BigDecimal.valueOf(weights.preference())));
        return new ScoreResult(
                total.divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP), breakdown);
    }

    /** 六个分项分数（0-100）。 */
    public record ScoreInputs(int areaScore, int workloadScore, int qualityScore,
                              int skillScore, int continuityScore, int preferenceScore) {
    }

    /**
     * 权重配置（docs/modules/08 第 7 节默认值 area25/workload20/quality20/skill15/
     * continuity10/preference10）。可从策略表 {@code weights_json} 解析覆盖。
     */
    public record ScoreWeights(int area, int workload, int quality, int skill,
                               int continuity, int preference) {

        public static final ScoreWeights DEFAULT =
                new ScoreWeights(25, 20, 20, 15, 10, 10);
    }

    /** 评分结果：总分（3 位小数）与分项拆解。 */
    public record ScoreResult(BigDecimal totalScore, Map<String, Integer> breakdown) {
    }
}
