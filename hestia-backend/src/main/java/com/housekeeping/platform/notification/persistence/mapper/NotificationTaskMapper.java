package com.housekeeping.platform.notification.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.notification.persistence.entity.NotificationTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知任务 Mapper。
 *
 * <p>状态推进全部使用条件更新（{@code WHERE status = 期望值}），
 * 影响行数为 0 即并发冲突或状态已变——任务可能被运营人工重试、
 * 被另一个调度实例认领，调用方必须以返回行数为准，
 * 禁止用 {@code updateById} 无条件覆盖状态（docs/modules/12 第 6 节）。</p>
 */
@Mapper
public interface NotificationTaskMapper extends BaseMapper<NotificationTaskEntity> {

    /**
     * 取出到期待发送的任务。
     *
     * <p>到期判定：scheduled_at 已到，且（无退避限制 或 退避时刻已过）。
     * 结果按创建时间升序，先到先发。</p>
     *
     * @param now   当前时刻
     * @param limit 单批上限
     * @return 待发送任务列表
     */
    @Select("SELECT * FROM notification_task "
            + "WHERE status = 'PENDING' AND scheduled_at <= #{now} "
            + "AND (next_retry_at IS NULL OR next_retry_at <= #{now}) "
            + "ORDER BY created_at ASC LIMIT #{limit}")
    List<NotificationTaskEntity> selectDueTasks(@Param("now") LocalDateTime now,
                                                @Param("limit") int limit);

    /**
     * 认领任务：PENDING → PROCESSING。
     *
     * @param id 任务 ID
     * @return 影响行数（0 = 已被他人认领或状态已变）
     */
    @Update("UPDATE notification_task SET status = 'PROCESSING', version = version + 1 "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markProcessing(@Param("id") long id);

    /**
     * 发送成功：PROCESSING → SENT。
     *
     * @param id    任务 ID
     * @param sentAt 发送时刻
     * @return 影响行数
     */
    @Update("UPDATE notification_task SET status = 'SENT', sent_at = #{sentAt}, "
            + "next_retry_at = NULL, last_error = NULL, version = version + 1 "
            + "WHERE id = #{id} AND status = 'PROCESSING'")
    int markSent(@Param("id") long id, @Param("sentAt") LocalDateTime sentAt);

    /**
     * 失败重试：PROCESSING → PENDING，推进失败计数并写入下次退避时刻。
     *
     * @param id          任务 ID
     * @param retryCount  新的失败计数
     * @param nextRetryAt 下次允许重试时刻
     * @param lastError   失败原因
     * @return 影响行数
     */
    @Update("UPDATE notification_task SET status = 'PENDING', retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, last_error = #{lastError}, version = version + 1 "
            + "WHERE id = #{id} AND status = 'PROCESSING' AND retry_count = #{retryCount} - 1")
    int markRetry(@Param("id") long id,
                  @Param("retryCount") int retryCount,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("lastError") String lastError);

    /**
     * 失败耗尽：PROCESSING → FAILED，等待人工处理。
     *
     * @param id        任务 ID
     * @param retryCount 最终失败计数
     * @param lastError 失败原因
     * @return 影响行数
     */
    @Update("UPDATE notification_task SET status = 'FAILED', retry_count = #{retryCount}, "
            + "last_error = #{lastError}, version = version + 1 "
            + "WHERE id = #{id} AND status = 'PROCESSING' AND retry_count = #{retryCount} - 1")
    int markFailedTerminal(@Param("id") long id,
                           @Param("retryCount") int retryCount,
                           @Param("lastError") String lastError);

    /**
     * 运营人工重试：FAILED → PENDING，清空退避与错误信息，立即参与调度。
     *
     * @param taskNo 任务编号
     * @return 影响行数（0 = 状态不是 FAILED，不允许重试）
     */
    @Update("UPDATE notification_task SET status = 'PENDING', next_retry_at = NULL, "
            + "last_error = NULL, version = version + 1 "
            + "WHERE task_no = #{taskNo} AND status = 'FAILED'")
    int requeueFailed(@Param("taskNo") String taskNo);

    /**
     * 按任务编号查询。
     *
     * @param taskNo 任务编号
     * @return 任务或 null
     */
    @Select("SELECT * FROM notification_task WHERE task_no = #{taskNo}")
    NotificationTaskEntity selectByTaskNo(@Param("taskNo") String taskNo);
}
