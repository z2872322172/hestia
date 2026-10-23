package com.housekeeping.platform.scheduling.application.command;

import java.time.LocalTime;

/**
 * 修改标准时间窗命令。
 *
 * <p>时间窗可能已被排班引用，修改起止时间只影响后续生成的时段，
 * 存量时段不变（docs/modules/06 第 19.2 节：服务项目变更不自动改变已生成时段，时间窗同理）。</p>
 *
 * @param windowName      展示名称
 * @param startTime       开始时间
 * @param endTime         结束时间
 * @param sortOrder       展示排序
 * @param status          ACTIVE / DISABLED
 * @param expectedVersion 乐观锁版本，不匹配即并发冲突
 */
public record UpdateServiceWindowCommand(String windowName, LocalTime startTime, LocalTime endTime,
                                         Integer sortOrder, String status, Long expectedVersion) {
}
