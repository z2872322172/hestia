package com.housekeeping.platform.location.application.command;

import java.time.LocalDateTime;

/**
 * 修改服务区域命令。
 *
 * <p>{@code version} 必填：服务区域的覆盖与优先级决定匹配结果，
 * 两个运营同时改时必须以乐观锁拒绝后到者，而不是让后写覆盖前写。</p>
 *
 * @param areaName      名称
 * @param description   说明
 * @param priority      优先级
 * @param effectiveFrom 生效开始时间
 * @param effectiveTo   生效结束时间
 * @param version       客户端持有的版本号
 */
public record UpdateServiceAreaCommand(String areaName, String description, Integer priority,
                                       LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
                                       Long version) {
}
