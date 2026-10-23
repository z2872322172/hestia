package com.housekeeping.platform.common.api;

import java.util.List;

/**
 * 统一分页结构。
 *
 * <p>字段名固定为 {@code records / page / size / total}（docs/07 第 13.3 节）。
 * {@code page} 从 1 开始计数，{@code size} 为单页条数。</p>
 *
 * @param records 当前页数据
 * @param page    当前页码，从 1 开始
 * @param size    单页条数
 * @param total   总记录数
 * @param <T>     记录类型
 */
public record PageResult<T>(List<T> records, int page, int size, long total) {

    /**
     * 构造分页结果，并对空集合做兜底，避免前端收到 {@code null} 后还要判空。
     *
     * @param records 当前页数据，可为 {@code null}
     * @param page    当前页码
     * @param size    单页条数
     * @param total   总记录数
     * @param <T>     记录类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, int page, int size, long total) {
        return new PageResult<>(records == null ? List.of() : records, page, size, total);
    }

    /**
     * 空分页结果。
     *
     * @param page 当前页码
     * @param size 单页条数
     * @param <T>  记录类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), page, size, 0L);
    }
}
