package com.housekeeping.platform.workforce.application.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人员档案摘要（列表与本人视角）。
 *
 * <p>不含手机号：手机号属于账号信息，由身份模块脱敏后返回。
 * 在人员模块再拼一份手机号，会让"哪里能看到手机号"出现第二个入口，
 * 脱敏规则一旦只改了一处就漏了。</p>
 *
 * @param workerNo            人员编号
 * @param realName            真实姓名
 * @param employmentType      用工类型
 * @param workStatus          工作状态
 * @param auditStatus         审核状态
 * @param experienceYears     从业年限
 * @param maxDailyOrders      每日最大接单量
 * @param rating              评分摘要（由服务质量模块回写）
 * @param completedOrderCount 完成订单数摘要
 * @param joinedAt            入职时间
 */
public record WorkerView(String workerNo,
                         String realName,
                         String employmentType,
                         String workStatus,
                         String auditStatus,
                         BigDecimal experienceYears,
                         int maxDailyOrders,
                         BigDecimal rating,
                         int completedOrderCount,
                         LocalDateTime joinedAt) {
}
