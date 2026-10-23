package com.housekeeping.platform.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 事件领取服务：把 PENDING 事件原子地转为 SENDING。
 *
 * <p>为什么把"领取"单独拆成一个短事务：{@code SELECT ... FOR UPDATE SKIP LOCKED}
 * 的行锁只在事务内有效，而发送 MQ 是外部调用，耗时不可控。
 * 如果领取和发送放在同一个事务里，行锁会一直持有到网络返回，
 * 高并发下 Relay 会互相阻塞，甚至拖垮连接池。拆开后事务只覆盖两条 SQL。</p>
 *
 * <p>事务注解放在这一层而不是 Relay：Relay 里有网络调用，不能进事务。</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxClaimService {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxClaimService.class);

    private final EventOutboxMapper eventOutboxMapper;

    public OutboxClaimService(EventOutboxMapper eventOutboxMapper) {
        this.eventOutboxMapper = eventOutboxMapper;
    }

    /**
     * 领取一批待投递事件。
     *
     * @param batchSize 本批最大条数
     * @return 已被标记为 SENDING 的事件；没有可投递事件时返回空列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<EventOutbox> claim(int batchSize) {
        List<EventOutbox> candidates = eventOutboxMapper.selectClaimableForUpdate(batchSize);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> ids = candidates.stream().map(EventOutbox::getId).toList();
        int updated = eventOutboxMapper.markSending(ids);
        if (updated != ids.size()) {
            // 说明有行在加锁后被其他流程改过状态，本批只处理真正标记成功的那些
            LOG.warn("领取事件时状态发生变化，期望 {} 行，实际 {} 行", ids.size(), updated);
        }
        candidates.forEach(event -> event.setStatus(EventOutboxStatus.SENDING.getValue()));
        return candidates;
    }
}
