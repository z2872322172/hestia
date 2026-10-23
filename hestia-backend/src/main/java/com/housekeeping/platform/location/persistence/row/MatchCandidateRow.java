package com.housekeeping.platform.location.persistence.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 候选服务区域的查询行。
 *
 * <p>用普通 POJO 而不是 record 接 MyBatis 结果：record 依赖构造器映射，
 * 需要额外开启 {@code argNameBasedConstructorAutoMapping} 且对列顺序敏感，
 * 一处改名就会静默得到 {@code null} 字段。</p>
 */
@Data
public class MatchCandidateRow {

    private Long serviceAreaId;

    private String areaNo;

    private String areaName;

    private String coverageLevel;

    private Integer priority;

    private LocalDateTime effectiveFrom;

    private String matchedRegionCode;
}
