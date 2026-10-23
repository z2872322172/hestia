package com.housekeeping.platform.workforce.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.workforce.persistence.entity.WorkerProfileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 人员档案 Mapper。
 */
@Mapper
public interface WorkerProfileMapper extends BaseMapper<WorkerProfileEntity> {

    /**
     * 按人员编号查询。
     *
     * @param workerNo 人员编号
     * @return 档案，不存在返回 {@code null}
     */
    @Select("SELECT * FROM worker_profile WHERE worker_no = #{workerNo}")
    WorkerProfileEntity selectByWorkerNo(@Param("workerNo") String workerNo);

    /**
     * 按账号查询（一个账号最多一个档案）。
     *
     * @param accountId 账号 ID
     * @return 档案，不存在返回 {@code null}
     */
    @Select("SELECT * FROM worker_profile WHERE account_id = #{accountId}")
    WorkerProfileEntity selectByAccountId(@Param("accountId") Long accountId);

    /**
     * 条件更新工作状态。
     *
     * <p>用 {@code WHERE work_status = #{expected}} 而不是"读出来判断再写"：
     * 两个运营同时点"暂停"和"离职"时，只有一个能成功，另一个拿到 0 行受影响
     * 从而得到明确的状态冲突，而不是后写覆盖先写。</p>
     *
     * @param id        人员 ID
     * @param expected  期望的当前状态
     * @param target    目标状态
     * @param leftAt    离职时间，非离职传 {@code null}
     * @param operator  操作人账号 ID
     * @return 影响行数，0 表示状态已被他人改变
     */
    @Update("UPDATE worker_profile SET work_status = #{target}, left_at = #{leftAt}, "
            + "updated_by_account_id = #{operator}, updated_at = NOW(3), version = version + 1 "
            + "WHERE id = #{id} AND work_status = #{expected}")
    int updateWorkStatus(@Param("id") Long id,
                         @Param("expected") String expected,
                         @Param("target") String target,
                         @Param("leftAt") LocalDateTime leftAt,
                         @Param("operator") Long operator);

    /**
     * 条件更新审核状态。
     *
     * @param id       人员 ID
     * @param expected 期望的当前审核状态
     * @param target   目标审核状态
     * @param operator 操作人账号 ID
     * @return 影响行数，0 表示审核状态已被他人改变
     */
    @Update("UPDATE worker_profile SET audit_status = #{target}, "
            + "updated_by_account_id = #{operator}, updated_at = NOW(3), version = version + 1 "
            + "WHERE id = #{id} AND audit_status = #{expected}")
    int updateAuditStatus(@Param("id") Long id,
                          @Param("expected") String expected,
                          @Param("target") String target,
                          @Param("operator") Long operator);

    /**
     * 记录入职时间。
     *
     * @param id       人员 ID
     * @param joinedAt 入职时间
     * @return 影响行数
     */
    @Update("UPDATE worker_profile SET joined_at = #{joinedAt}, updated_at = NOW(3), "
            + "version = version + 1 WHERE id = #{id} AND joined_at IS NULL")
    int markJoined(@Param("id") Long id, @Param("joinedAt") LocalDateTime joinedAt);

    /**
     * 统计某审核状态下的档案数量（运营看板与冒烟断言用）。
     *
     * @param auditStatus 审核状态
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM worker_profile WHERE audit_status = #{auditStatus}")
    int countByAuditStatus(@Param("auditStatus") String auditStatus);
}
