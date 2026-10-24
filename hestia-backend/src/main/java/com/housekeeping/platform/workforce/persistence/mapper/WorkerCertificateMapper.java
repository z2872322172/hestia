package com.housekeeping.platform.workforce.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.workforce.persistence.entity.WorkerCertificateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 人员证书 Mapper。
 *
 * <p>注意：查询语句<b>不</b>返回 {@code certificate_no_cipher} 之外的任何派生信息，
 * 也不在 SQL 里做解密。解密只发生在应用层，且需要单独权限——证书编号一旦进日志或进响应，
 * 就等于泄漏。</p>
 */
@Mapper
public interface WorkerCertificateMapper extends BaseMapper<WorkerCertificateEntity> {

    /**
     * 查询人员的全部证书（运营视角，含待审/驳回/过期）。
     *
     * @param workerId 人员 ID
     * @return 证书列表
     */
    @Select("SELECT id, worker_id, certificate_type, file_id, issued_at, expires_at, "
            + "audit_status, valid_status, verified_by_account_id, verified_at, remark, "
            + "created_by_account_id, updated_by_account_id, version, created_at, updated_at "
            + "FROM worker_certificate WHERE worker_id = #{workerId} ORDER BY id ASC")
    List<WorkerCertificateEntity> selectByWorkerId(@Param("workerId") Long workerId);

    /**
     * 查询人员可用于派单资格的证书（已审核通过且有效）。
     *
     * <p>到期日过滤放在 SQL 里，但只作为粗筛：真正的"是否过期"由
     * {@code CertificateValidity} 判定，避免数据库时区与 JVM 时区不一致时两边结论打架。</p>
     *
     * @param workerId 人员 ID
     * @param today    基准日
     * @return 证书列表
     */
    @Select("SELECT id, worker_id, certificate_type, file_id, issued_at, expires_at, "
            + "audit_status, valid_status, verified_by_account_id, verified_at, remark, "
            + "created_by_account_id, updated_by_account_id, version, created_at, updated_at "
            + "FROM worker_certificate WHERE worker_id = #{workerId} "
            + "AND audit_status = 'APPROVED' AND valid_status = 'VALID' "
            + "AND (expires_at IS NULL OR expires_at >= #{today})")
    List<WorkerCertificateEntity> selectUsableByWorkerId(@Param("workerId") Long workerId,
                                                         @Param("today") LocalDate today);

    /**
     * 把已到期但仍标记为 VALID 的证书刷成 EXPIRED。
     *
     * <p>只改 {@code valid_status}，不动 {@code audit_status}：证书确实被审核过，
     * 只是时间到了。合并两个字段会让"换新证后恢复资格"需要伪造审核记录。</p>
     *
     * @param today 基准日
     * @return 影响行数
     */
    @Update("UPDATE worker_certificate SET valid_status = 'EXPIRED', updated_at = NOW(3) "
            + "WHERE valid_status = 'VALID' AND expires_at IS NOT NULL AND expires_at < #{today}")
    int markExpired(@Param("today") LocalDate today);

    /**
     * 查询即将到期的证书（提前提醒用）。
     *
     * @param from 起始日（含）
     * @param to   截止日（含）
     * @return 证书列表
     */
    @Select("SELECT id, worker_id, certificate_type, file_id, issued_at, expires_at, "
            + "audit_status, valid_status, verified_by_account_id, verified_at, remark, "
            + "created_by_account_id, updated_by_account_id, version, created_at, updated_at "
            + "FROM worker_certificate WHERE valid_status = 'VALID' "
            + "AND expires_at BETWEEN #{from} AND #{to} ORDER BY expires_at ASC")
    List<WorkerCertificateEntity> selectExpiringBetween(@Param("from") LocalDate from,
                                                        @Param("to") LocalDate to);

    /**
     * 统计证书即将到期且服务区域命中的去重人员数（运营告警用，docs/modules/13 第 5 节）。
     *
     * <p>区域归属来自 {@code worker_service_area}（人员自己的关系表，只读）；
     * 同一人员多个区域命中只计一次。areaIds 为空 = 不限区域（ADMIN）。</p>
     *
     * @param areaIds 授权服务区域 ID 集合（可空 = 不限）
     * @param from    起始日（含）
     * @param to      截止日（含）
     * @return 去重人员数
     */
    @Select("<script>SELECT COUNT(DISTINCT wc.worker_id) FROM worker_certificate wc "
            + "JOIN worker_service_area wsa ON wsa.worker_id = wc.worker_id "
            + "AND wsa.status = 'ACTIVE' "
            + "WHERE wc.valid_status = 'VALID' AND wc.expires_at BETWEEN #{from} AND #{to} "
            + "<if test='areaIds != null and areaIds.size() > 0'>"
            + "AND wsa.service_area_id IN "
            + "<foreach collection='areaIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</if></script>")
    long countExpiringWorkers(@Param("areaIds") Collection<Long> areaIds,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);
}
