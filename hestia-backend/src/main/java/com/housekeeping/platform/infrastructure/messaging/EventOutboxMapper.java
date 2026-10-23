package com.housekeeping.platform.infrastructure.messaging;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * {@code event_outbox} 数据访问。
 *
 * <p>复杂 SQL 写在注解里并附原因，避免 XML 与实体字段漂移后无人发现。</p>
 */
@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {

    /**
     * 领取一批待投递事件。
     *
     * <p>必须使用 {@code FOR UPDATE SKIP LOCKED}：多个 Relay 实例并发扫描时，
     * 已加锁的行会被直接跳过，而不是排队等待，从而避免同一事件被两个实例投递两次。
     * 该语句必须在事务内执行，否则行锁立即释放，跳过语义失效。</p>
     *
     * @param limit 本批最大条数
     * @return 已加锁的事件列表
     */
    @Select("""
            SELECT * FROM event_outbox
             WHERE status = 'PENDING'
               AND (next_retry_at IS NULL OR next_retry_at <= NOW(3))
             ORDER BY id ASC
             LIMIT #{limit}
             FOR UPDATE SKIP LOCKED
            """)
    List<EventOutbox> selectClaimableForUpdate(@Param("limit") int limit);

    /**
     * 把领取到的事件标记为 SENDING。
     *
     * <p>只有状态仍为 PENDING 才更新，防止极端情况下覆盖已被其他实例处理的状态。</p>
     *
     * @param ids 事件主键列表，调用方保证非空
     * @return 实际更新行数
     */
    @Update("""
            <script>
            UPDATE event_outbox
               SET status = 'SENDING'
             WHERE status = 'PENDING'
               AND id IN
               <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int markSending(@Param("ids") List<Long> ids);
}
