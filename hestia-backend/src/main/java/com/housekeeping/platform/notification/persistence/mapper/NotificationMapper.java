package com.housekeeping.platform.notification.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.notification.persistence.entity.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 站内通知 Mapper。
 *
 * <p>已读操作全部以 {@code recipient_account_id} 为条件：
 * 归属校验在 SQL 里兜底，即使应用层漏判也不会改到别人的消息。</p>
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {

    /**
     * 按通知编号查询。
     *
     * @param notificationNo 通知编号
     * @return 通知或 null
     */
    @Select("SELECT * FROM notification WHERE notification_no = #{notificationNo}")
    NotificationEntity selectByNotificationNo(@Param("notificationNo") String notificationNo);

    /**
     * 统计未读数。
     *
     * @param accountId 收件账号 ID
     * @return 未读数
     */
    @Select("SELECT COUNT(*) FROM notification "
            + "WHERE recipient_account_id = #{accountId} AND read_at IS NULL")
    long countUnread(@Param("accountId") long accountId);

    /**
     * 标记单条已读（仅收件人本人、仅未读可标记；重复已读幂等返回 0）。
     *
     * @param notificationNo 通知编号
     * @param accountId       当前账号 ID
     * @param readAt          已读时刻
     * @return 影响行数
     */
    @Update("UPDATE notification SET read_at = #{readAt}, version = version + 1 "
            + "WHERE notification_no = #{notificationNo} "
            + "AND recipient_account_id = #{accountId} AND read_at IS NULL")
    int markRead(@Param("notificationNo") String notificationNo,
                 @Param("accountId") long accountId,
                 @Param("readAt") LocalDateTime readAt);

    /**
     * 全部标记已读。
     *
     * @param accountId 收件账号 ID
     * @param readAt    已读时刻
     * @return 影响行数
     */
    @Update("UPDATE notification SET read_at = #{readAt}, version = version + 1 "
            + "WHERE recipient_account_id = #{accountId} AND read_at IS NULL")
    int markAllRead(@Param("accountId") long accountId,
                    @Param("readAt") LocalDateTime readAt);
}
