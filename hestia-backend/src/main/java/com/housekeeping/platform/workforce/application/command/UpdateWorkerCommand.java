package com.housekeeping.platform.workforce.application.command;

import java.math.BigDecimal;

/**
 * 修改人员档案命令。
 *
 * <p>{@code expectedVersion} 必填：人员档案会被运营和本人同时修改
 * （运营改接单上限、本人改自我介绍），没有乐观锁就是后写覆盖先写。</p>
 *
 * @param realName        真实姓名
 * @param gender          性别，可空
 * @param employmentType  用工类型
 * @param experienceYears 从业年限
 * @param introduction    自我介绍
 * @param maxDailyOrders  每日最大接单量
 * @param expectedVersion 客户端读到的版本号
 */
public record UpdateWorkerCommand(String realName,
                                  String gender,
                                  String employmentType,
                                  BigDecimal experienceYears,
                                  String introduction,
                                  Integer maxDailyOrders,
                                  long expectedVersion) {
}
