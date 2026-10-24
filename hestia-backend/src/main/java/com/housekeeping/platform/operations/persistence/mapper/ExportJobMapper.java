package com.housekeeping.platform.operations.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.operations.persistence.entity.ExportJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 数据导出任务 Mapper（docs/modules/13 第 11 节）。
 *
 * <p>状态推进全部条件更新：认领、完成、失败、取消只允许从预期状态出发，
 * 影响行数 0 即并发冲突（重复执行、已被取消等），调用方据此跳过或报错。</p>
 */
@Mapper
public interface ExportJobMapper extends BaseMapper<ExportJobEntity> {

    /**
     * 按导出编号查询。
     *
     * @param exportNo 导出编号
     * @return 任务或 null
     */
    @Select("SELECT * FROM export_job WHERE export_no = #{exportNo}")
    ExportJobEntity selectByExportNo(@Param("exportNo") String exportNo);

    /**
     * 认领任务：PENDING → PROCESSING。
     *
     * @param id 任务 ID
     * @return 影响行数
     */
    @Update("UPDATE export_job SET status = 'PROCESSING', started_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE id = #{id} AND status = 'PENDING'")
    int claim(@Param("id") long id);

    /**
     * 完成任务：PROCESSING → SUCCESS（回填文件、行数与过期时间）。
     *
     * @param id        任务 ID
     * @param fileId    file_object 主键
     * @param rowCount  导出行数
     * @param expiresAt 文件过期时间
     * @return 影响行数
     */
    @Update("UPDATE export_job SET status = 'SUCCESS', file_id = #{fileId}, row_count = #{rowCount}, "
            + "expires_at = #{expiresAt}, finished_at = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{id} AND status = 'PROCESSING'")
    int finish(@Param("id") long id,
               @Param("fileId") long fileId,
               @Param("rowCount") int rowCount,
               @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 标记失败：PROCESSING → FAILED（错误原因截断由调用方负责）。
     *
     * @param id    任务 ID
     * @param error 失败原因
     * @return 影响行数
     */
    @Update("UPDATE export_job SET status = 'FAILED', error_message = #{error}, "
            + "finished_at = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{id} AND status = 'PROCESSING'")
    int markFailed(@Param("id") long id, @Param("error") String error);

    /**
     * 取消待处理任务：PENDING → CANCELED。
     *
     * @param id 任务 ID
     * @return 影响行数
     */
    @Update("UPDATE export_job SET status = 'CANCELED', finished_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE id = #{id} AND status = 'PENDING'")
    int cancelPending(@Param("id") long id);

    /**
     * 把文件已过期的成功任务刷成 EXPIRED（到期清理）。
     *
     * @param asOf 时间基准
     * @return 影响行数
     */
    @Update("UPDATE export_job SET status = 'EXPIRED', version = version + 1 "
            + "WHERE status = 'SUCCESS' AND expires_at IS NOT NULL AND expires_at < #{asOf}")
    int expireDue(@Param("asOf") LocalDateTime asOf);
}
