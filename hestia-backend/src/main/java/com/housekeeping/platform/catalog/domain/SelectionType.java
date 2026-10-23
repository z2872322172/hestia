package com.housekeeping.platform.catalog.domain;

/**
 * 规格组的选择方式（docs/modules/04 第 7.1 节）。
 */
public enum SelectionType {

    /** 单选：最多只能选一个值。 */
    SINGLE,

    /** 多选：可选数量受 {@code min_select} / {@code max_select} 限制。 */
    MULTIPLE;

    /**
     * 是否单选。
     *
     * @return 单选返回 {@code true}
     */
    public boolean isSingle() {
        return this == SINGLE;
    }
}
