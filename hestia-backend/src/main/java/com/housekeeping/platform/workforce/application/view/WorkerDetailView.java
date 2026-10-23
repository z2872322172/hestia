package com.housekeeping.platform.workforce.application.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人员档案详情（运营视角）。
 *
 * @param workerNo            人员编号
 * @param accountNo           绑定账号编号
 * @param realName            真实姓名
 * @param gender              性别
 * @param employmentType      用工类型
 * @param workStatus          工作状态
 * @param auditStatus         审核状态
 * @param experienceYears     从业年限
 * @param introduction        自我介绍
 * @param maxDailyOrders      每日最大接单量
 * @param rating              评分摘要
 * @param completedOrderCount 完成订单数摘要
 * @param joinedAt            入职时间
 * @param leftAt              离职时间
 * @param skills              技能列表
 * @param certificates        证书列表
 * @param serviceAreas        服务区域列表
 */
public record WorkerDetailView(String workerNo,
                               String accountNo,
                               String realName,
                               String gender,
                               String employmentType,
                               String workStatus,
                               String auditStatus,
                               BigDecimal experienceYears,
                               String introduction,
                               int maxDailyOrders,
                               BigDecimal rating,
                               int completedOrderCount,
                               LocalDateTime joinedAt,
                               LocalDateTime leftAt,
                               List<SkillView> skills,
                               List<CertificateView> certificates,
                               List<WorkerServiceAreaView> serviceAreas) {
}
