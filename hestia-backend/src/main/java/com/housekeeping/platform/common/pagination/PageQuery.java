package com.housekeeping.platform.common.pagination;

/**
 * 分页请求参数。
 *
 * <p>默认 {@code page=1}、{@code size=10}，单页上限 100（docs/03 第 9.4 节）。
 * 上限必须由服务端强制收敛：直接信任前端传入的 size 会让一次请求拖垮数据库。</p>
 */
public final class PageQuery {

    public static final int DEFAULT_PAGE = 1;

    public static final int DEFAULT_SIZE = 10;

    public static final int MAX_SIZE = 100;

    private final int page;

    private final int size;

    private PageQuery(int page, int size) {
        this.page = page;
        this.size = size;
    }

    /**
     * 归一化分页参数。
     *
     * @param page 页码，小于 1 时取 1
     * @param size 单页条数，小于 1 时取默认值，超过 100 时截断为 100
     * @return 归一化后的分页参数
     */
    public static PageQuery of(Integer page, Integer size) {
        int normalizedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int normalizedSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageQuery(normalizedPage, normalizedSize);
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    /**
     * 换算为 SQL OFFSET。
     *
     * @return 偏移量，使用 long 避免大页码相乘溢出
     */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
