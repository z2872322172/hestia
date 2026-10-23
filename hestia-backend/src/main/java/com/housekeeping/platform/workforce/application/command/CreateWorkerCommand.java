package com.housekeeping.platform.workforce.application.command;

import java.math.BigDecimal;

/**
 * 创建人员档案命令（运营端）。
 *
 * <p>创建人员与开户是一件事：运营填了手机号，系统就建（或复用）账号、授 {@code WORKER} 角色、
 * 生成一次性激活凭证，同时建人员档案。分成两个接口的话，运营很容易只建了档案
 * 却忘了邀请，人员档案挂在一个永远无法登录的账号上。</p>
 *
 * @param countryCode     手机号区号
 * @param phone           手机号
 * @param displayName     账号显示名
 * @param realName        真实姓名
 * @param gender          性别，可空
 * @param employmentType  用工类型
 * @param experienceYears 从业年限，可空按 0 处理
 * @param introduction    自我介绍，可空
 * @param maxDailyOrders  每日最大接单量，可空按默认值处理
 */
public record CreateWorkerCommand(String countryCode,
                                  String phone,
                                  String displayName,
                                  String realName,
                                  String gender,
                                  String employmentType,
                                  BigDecimal experienceYears,
                                  String introduction,
                                  Integer maxDailyOrders) {
}
