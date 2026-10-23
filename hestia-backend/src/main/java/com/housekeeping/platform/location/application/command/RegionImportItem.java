package com.housekeeping.platform.location.application.command;

/**
 * 行政区导入项。
 *
 * <p>导入是<b>幂等</b>的：同一 {@code regionCode} 重复导入只更新名称与状态，不重复插入。
 * 这样运营可以反复用最新国家行政区划文件覆盖，而不会产生重复区县。</p>
 *
 * @param regionCode  行政区编码
 * @param parentCode  上级行政区编码，省级为 {@code null}
 * @param regionLevel 层级
 * @param regionName  名称
 * @param shortName   简称
 * @param sortOrder   排序号
 */
public record RegionImportItem(String regionCode, String parentCode, String regionLevel,
                               String regionName, String shortName, Integer sortOrder) {
}
