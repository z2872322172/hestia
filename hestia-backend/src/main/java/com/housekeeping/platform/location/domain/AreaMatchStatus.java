package com.housekeeping.platform.location.domain;

/**
 * 地址匹配结果状态（docs/modules/03 第 6.4 节）。
 *
 * <p>与客户地址的 {@code area_match_status} 不是同一套值：
 * 位置模块会返回 {@link #AMBIGUOUS} 和 {@link #REGION_INVALID}，
 * 客户模块需要把它们映射成 {@code UNMATCHED} 并给出明确错误，
 * <b>不能</b>伪装成普通的"不在服务范围"——否则运营永远查不出配置冲突。</p>
 */
public enum AreaMatchStatus {

    /** 匹配到唯一服务区域。 */
    MATCHED,

    /** 没有服务区域覆盖。 */
    OUT_OF_RANGE,

    /** 存在同级同优先级冲突，需要运营人工处理。 */
    AMBIGUOUS,

    /** 行政区编码无效或层级错误。 */
    REGION_INVALID
}
