package com.housekeeping.platform.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.notification.domain.NotificationAudience;
import com.housekeeping.platform.notification.domain.NotificationRetryBackoff;
import com.housekeeping.platform.notification.domain.TemplateRenderer;
import com.housekeeping.platform.notification.persistence.entity.NotificationDeliveryEntity;
import com.housekeeping.platform.notification.persistence.entity.NotificationEntity;
import com.housekeeping.platform.notification.persistence.entity.NotificationTaskEntity;
import com.housekeeping.platform.notification.persistence.entity.NotificationTemplateEntity;
import com.housekeeping.platform.notification.persistence.mapper.NotificationDeliveryMapper;
import com.housekeeping.platform.notification.persistence.mapper.NotificationMapper;
import com.housekeeping.platform.notification.persistence.mapper.NotificationTaskMapper;
import com.housekeeping.platform.notification.persistence.mapper.NotificationTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通知发送调度器：扫描到期任务 → 渲染模板 → 落站内消息与发送记录 → 标记 SENT
 * （docs/modules/12 第 9 节）。
 *
 * <p>并发安全靠三层条件更新/唯一键，任何一层未命中都放弃而不是强改：</p>
 * <ol>
 *   <li>认领：{@code PENDING → PROCESSING} 条件更新，多实例调度不重复发；</li>
 *   <li>站内消息：{@code task_id} 唯一索引——认领后崩溃重投时插入冲突按"已发送"处理，
 *       这是比"先查后插"可靠的幂等点（查询与插入之间仍有窗口，唯一键没有）；</li>
 *   <li>状态推进：全部 WHERE status = 'PROCESSING'。</li>
 * </ol>
 *
 * <p>通知失败不回滚业务：所有异常在本任务内消化为重试 / FAILED，
 * 绝不向上抛（docs/modules/12 第 1 节核心原则）。</p>
 */
@Component
@RequiresPersistence
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled",
        havingValue = "true", matchIfMissing = true)
public class NotificationDispatchJob {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatchJob.class);

    /** 单批处理上限，防止一次扫描拖太久。 */
    private static final int BATCH_SIZE = 50;

    private static final String CHANNEL_IN_APP = "IN_APP";

    private static final String BUSINESS_TYPE_BOOKING = "BOOKING";

    private static final String NOTIFICATION_NO_SEQ_KEY = "NOTIFICATION";
    private static final String NOTIFICATION_NO_PREFIX = "NTF";

    /** 数据库列宽兜底：last_error / error_message 500，title 255，content 2000。 */
    private static final int ERROR_MAX_LENGTH = 500;
    private static final int TITLE_MAX_LENGTH = 255;
    private static final int CONTENT_MAX_LENGTH = 2000;

    private final NotificationTaskMapper taskMapper;

    private final NotificationTemplateMapper templateMapper;

    private final NotificationMapper notificationMapper;

    private final NotificationDeliveryMapper deliveryMapper;

    private final BusinessNoGenerator businessNoGenerator;

    private final ObjectMapper objectMapper;

    public NotificationDispatchJob(NotificationTaskMapper taskMapper,
                                   NotificationTemplateMapper templateMapper,
                                   NotificationMapper notificationMapper,
                                   NotificationDeliveryMapper deliveryMapper,
                                   BusinessNoGenerator businessNoGenerator,
                                   ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.templateMapper = templateMapper;
        this.notificationMapper = notificationMapper;
        this.deliveryMapper = deliveryMapper;
        this.businessNoGenerator = businessNoGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 定时扫描；间隔由 {@code app.notification.dispatch-interval-ms} 配置。
     */
    @Scheduled(fixedDelayString = "${app.notification.dispatch-interval-ms:15000}",
            initialDelayString = "${app.notification.dispatch-interval-ms:15000}")
    public void scheduledDispatch() {
        runOnce();
    }

    /**
     * 执行一轮发送。
     *
     * @return 本轮认领并处理（含失败）的任务数
     */
    public int runOnce() {
        return runOnce(LocalDateTime.now());
    }

    /**
     * 执行一轮发送（可注入模拟时刻，冒烟脚本用）。
     *
     * @param now 模拟当前时刻
     * @return 本轮处理的任务数
     */
    public int runOnce(LocalDateTime now) {
        List<NotificationTaskEntity> dueTasks = taskMapper.selectDueTasks(now, BATCH_SIZE);
        int handled = 0;
        for (NotificationTaskEntity task : dueTasks) {
            if (taskMapper.markProcessing(task.getId()) == 0) {
                // 已被其他实例 / 人工重试路径认领
                continue;
            }
            handled++;
            send(task, now);
        }
        return handled;
    }

    /**
     * 发送单个任务；所有异常消化为重试或终态失败。
     */
    private void send(NotificationTaskEntity task, LocalDateTime now) {
        try {
            renderAndDeliver(task, now);
            taskMapper.markSent(task.getId(), now);
        } catch (RuntimeException exception) {
            String error = truncate(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
            LOG.warn("通知任务发送失败 taskNo={} 原因={}", task.getTaskNo(), error);
            recordFailure(task, error, now);
        }
    }

    /**
     * 渲染并落站内消息。成功或"已存在"（幂等）都正常返回，其余抛出。
     */
    private void renderAndDeliver(NotificationTaskEntity task, LocalDateTime now) {
        NotificationTemplateEntity template = templateMapper.selectActive(
                task.getEventType(), audienceName(task), task.getChannel());
        if (template == null) {
            throw new IllegalStateException("模板不存在或已停用 templateCode=" + task.getTemplateCode());
        }
        Map<String, Object> variables;
        try {
            variables = objectMapper.readValue(
                    task.getPayloadJson(), new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            // 载荷是本模块写入的 JSON，解析失败说明数据被破坏，按任务失败走重试/人工
            throw new IllegalStateException("任务载荷解析失败 taskNo=" + task.getTaskNo(), exception);
        }
        TemplateRenderer.RenderResult rendered = TemplateRenderer.render(
                template.getTitleTemplate(), template.getContentTemplate(), variables);
        if (!rendered.ok()) {
            throw new IllegalStateException("模板渲染失败：" + rendered.errorMessage());
        }
        NotificationEntity notification = new NotificationEntity();
        notification.setNotificationNo(
                businessNoGenerator.next(NOTIFICATION_NO_SEQ_KEY, NOTIFICATION_NO_PREFIX));
        notification.setTaskId(task.getId());
        notification.setRecipientAccountId(task.getRecipientAccountId());
        notification.setTitle(truncate(rendered.title(), TITLE_MAX_LENGTH));
        notification.setContent(truncate(rendered.content(), CONTENT_MAX_LENGTH));
        notification.setBusinessType(BUSINESS_TYPE_BOOKING);
        Object orderNo = variables.get("orderNo");
        notification.setBusinessId(orderNo == null ? null : String.valueOf(orderNo));
        notification.setPriority(task.getPriority());
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException exception) {
            // task_id 唯一：上一轮已发送成功但状态推进前崩溃，按幂等成功处理
            LOG.info("站内消息已存在，按已发送处理 taskNo={}", task.getTaskNo());
            recordDelivery(task, "SENT", null, now);
            return;
        }
        recordDelivery(task, "SENT", null, now);
    }

    /**
     * 失败收尾：先留发送记录（排障依据），再按退避策略回 PENDING 或进 FAILED。
     */
    private void recordFailure(NotificationTaskEntity task, String error, LocalDateTime now) {
        recordDelivery(task, "FAILED", error, now);
        int newRetryCount = task.getRetryCount() + 1;
        if (NotificationRetryBackoff.exhausted(newRetryCount, task.getMaxRetryCount())) {
            taskMapper.markFailedTerminal(task.getId(), newRetryCount, error);
        } else {
            taskMapper.markRetry(task.getId(), newRetryCount,
                    NotificationRetryBackoff.nextRetryAt(newRetryCount, now), error);
        }
    }

    /**
     * 写发送记录。记录失败不影响任务状态推进（只追加表，丢一条记录可接受），
     * 但要留日志——attempt_no 冲突说明同轮重试，属于异常路径。
     */
    private void recordDelivery(NotificationTaskEntity task, String status,
                                String errorMessage, LocalDateTime now) {
        try {
            NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
            delivery.setTaskId(task.getId());
            delivery.setChannel(task.getChannel());
            delivery.setAttemptNo(task.getRetryCount() + 1);
            delivery.setStatus(status);
            delivery.setErrorMessage(errorMessage == null ? null : truncate(errorMessage));
            delivery.setSentAt("SENT".equals(status) ? now : null);
            deliveryMapper.insert(delivery);
        } catch (RuntimeException exception) {
            LOG.warn("通知发送记录写入失败 taskNo={} 原因={}", task.getTaskNo(), exception.getMessage());
        }
    }

    private String audienceName(NotificationTaskEntity task) {
        // 收件角色与受众枚举同名；防御性兜底避免脏数据拖垮整轮调度
        try {
            return NotificationAudience.valueOf(task.getRecipientRole()).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("未知收件角色 " + task.getRecipientRole(), exception);
        }
    }

    private String truncate(String text) {
        return truncate(text, ERROR_MAX_LENGTH);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
