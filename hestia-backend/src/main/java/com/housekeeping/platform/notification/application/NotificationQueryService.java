package com.housekeeping.platform.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.notification.application.view.NotificationView;
import com.housekeeping.platform.notification.persistence.entity.NotificationEntity;
import com.housekeeping.platform.notification.persistence.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 站内消息查询与已读服务（docs/modules/12 第 15.1 节）。
 *
 * <p>数据范围：只按当前账号 ID 过滤，"别人的消息"一律回
 * {@code NOTIFICATION_NOT_OWNED}（404，与不存在同文案），
 * 防止通过错误码差异枚举他人通知编号。</p>
 */
@Service
@RequiresPersistence
public class NotificationQueryService {

    private final NotificationMapper notificationMapper;

    public NotificationQueryService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 收件箱分页。
     *
     * @param accountId  当前账号 ID
     * @param onlyUnread 只看未读
     * @param page       页码
     * @param size       条数
     * @return 分页通知
     */
    @Transactional(readOnly = true)
    public PageResult<NotificationView> inbox(long accountId, boolean onlyUnread,
                                              int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getRecipientAccountId, accountId)
                .isNull(onlyUnread, NotificationEntity::getReadAt)
                .orderByDesc(NotificationEntity::getId);
        Page<NotificationEntity> result =
                notificationMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toView).toList(),
                safePage, safeSize, result.getTotal());
    }

    /**
     * 未读数量（前端红点）。
     *
     * @param accountId 当前账号 ID
     * @return 未读数
     */
    @Transactional(readOnly = true)
    public long unreadCount(long accountId) {
        return notificationMapper.countUnread(accountId);
    }

    /**
     * 标记单条已读。重复已读幂等（SQL 条件 read_at IS NULL 未命中时按成功返回）。
     *
     * @param accountId      当前账号 ID
     * @param notificationNo 通知编号
     * @throws BusinessException 通知不存在或不属于当前账号
     */
    @Transactional
    public void markRead(long accountId, String notificationNo) {
        if (notificationMapper.markRead(notificationNo, accountId, LocalDateTime.now()) > 0) {
            return;
        }
        NotificationEntity entity = notificationMapper.selectByNotificationNo(notificationNo);
        if (entity == null || entity.getRecipientAccountId() == null
                || entity.getRecipientAccountId() != accountId) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_OWNED);
        }
        // 归属正确但条件未命中：说明已经是已读，幂等返回
    }

    /**
     * 全部标记已读。
     *
     * @param accountId 当前账号 ID
     * @return 本次标记的条数
     */
    @Transactional
    public long markAllRead(long accountId) {
        return notificationMapper.markAllRead(accountId, LocalDateTime.now());
    }

    private NotificationView toView(NotificationEntity entity) {
        return new NotificationView(entity.getNotificationNo(), entity.getTitle(),
                entity.getContent(), entity.getBusinessType(), entity.getBusinessId(),
                entity.getPriority(), entity.getReadAt(), entity.getCreatedAt());
    }
}
