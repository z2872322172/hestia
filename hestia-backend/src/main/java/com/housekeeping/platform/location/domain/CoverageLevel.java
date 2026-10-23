package com.housekeeping.platform.location.domain;

/**
 * 覆盖级别（docs/modules/03 第 7.3 节）。
 *
 * <p>街道级覆盖比区县级覆盖更精确，匹配时优先。{@code rank} 越小越优先，
 * 直接作为排序键使用，避免在 Service 里写"先判断是不是 STREET"这种散落逻辑。</p>
 */
public enum CoverageLevel {

    /** 街道级覆盖，最精确。 */
    STREET(0),

    /** 区县级覆盖。 */
    DISTRICT(1);

    private final int rank;

    CoverageLevel(int rank) {
        this.rank = rank;
    }

    /**
     * 排序权重，越小越优先。
     *
     * @return 权重
     */
    public int rank() {
        return rank;
    }
}
