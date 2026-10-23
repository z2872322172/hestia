package com.housekeeping.platform.workforce.domain;

import java.util.Map;
import java.util.Set;

/**
 * 资格判定所需的全部事实（一次查全，纯数据）。
 *
 * <p>把所有输入压成一个不可变快照，是为了让 {@link EligibilityEvaluator} 保持纯函数：
 * 判定过程中不查库、不取当前时间，因此每个原因都能被单独构造出来测试。
 * 如果判定逻辑散在服务里，想测"健康证过期"就得先准备一整套数据库数据。</p>
 *
 * @param accountStatus            账号状态，{@code null} 表示账号不存在
 * @param workerRoleActive         WORKER 角色是否为 {@code ACTIVE}
 * @param workStatus               人员工作状态
 * @param auditStatus              人员档案审核状态
 * @param serviceItemId            待派单的服务项目 ID
 * @param verifiedSkillItemIds     已审核且启用的技能对应的服务项目 ID 集合
 * @param requiredCertificateTypes 该服务项目要求的证书类型集合
 * @param certificateUsability     该人员每类证书的可用性（同类多张取最差）
 * @param activeServiceAreaIds     已启用的服务区域 ID 集合
 * @param requiredServiceAreaId    订单所在服务区域 ID，{@code null} 表示本次不校验区域覆盖
 */
public record WorkerFacts(String accountStatus,
                          boolean workerRoleActive,
                          WorkStatus workStatus,
                          AuditStatus auditStatus,
                          long serviceItemId,
                          Set<Long> verifiedSkillItemIds,
                          Set<CertificateType> requiredCertificateTypes,
                          Map<CertificateType, CertificateUsability> certificateUsability,
                          Set<Long> activeServiceAreaIds,
                          Long requiredServiceAreaId) {
}
