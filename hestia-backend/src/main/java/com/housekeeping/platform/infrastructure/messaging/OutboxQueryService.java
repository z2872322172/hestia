package com.housekeeping.platform.infrastructure.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 发件箱查询服务，供 smoke 接口与后续运营排查使用。
 */
@Service
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxQueryService {

    private final EventOutboxMapper eventOutboxMapper;

    public OutboxQueryService(EventOutboxMapper eventOutboxMapper) {
        this.eventOutboxMapper = eventOutboxMapper;
    }

    /**
     * 按事件 ID 查询发件箱记录。
     *
     * @param eventId 事件唯一标识
     * @return 事件记录；不存在时为空
     */
    public Optional<EventOutbox> findByEventId(String eventId) {
        LambdaQueryWrapper<EventOutbox> wrapper = new LambdaQueryWrapper<EventOutbox>()
                .eq(EventOutbox::getEventId, eventId)
                .last("LIMIT 1");
        return Optional.ofNullable(eventOutboxMapper.selectOne(wrapper));
    }
}
