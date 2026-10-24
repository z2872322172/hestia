package com.housekeeping.platform.dispatch.persistence.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 派单行为按日计数行（运营统计契约的 SQL 映射行，docs/modules/13 第 7.2 节）。
 *
 * <p>全公司口径：派单行为记录本身不携带区域属性。</p>
 */
@Data
public class AssignmentDateCountRow {

    /** 统计日期。 */
    private LocalDate statDate;

    /** 计数。 */
    private Long cnt;
}
