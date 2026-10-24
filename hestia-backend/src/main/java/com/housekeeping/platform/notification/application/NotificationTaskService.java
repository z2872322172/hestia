package com.housekeeping.platform.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.notification.application.view.NotificationTaskView;
import com.housekeeping.platform.notification.domain.NotificationEventTypes;
import com.housekeeping.platform.notification.persistence.entity.NotificationTemplateEntity;
import com.housekeeping.platform.notification.persistence.mapper.NotificationTemplateMapper;
import com.housekeeping.platform.notification.persistence.entity.NotificationTaskEntity;
import com.housekeeping.platform.notification.persistence.mapper.NotificationTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知任务服务（docs/07 §11.12 的 {@code NotificationTaskService} 契约）。
 *
 * <p>入口只有一个：{@link #createTasksForEvent}——事件消费方把已确认消费的
 * 领域事件交进来，这里解析收件人、选模板、按去重键落任务。
 * 不变量：</p>
 * <ul>
 *   <li><b>幂等</b>：dedupe_key = sourceEventId + 收件人 + 模板 + 渠道，
 *       唯一索引兜底，事件重复消费在这里被挡下（DuplicateKeyException 静默跳过）；</li>
 *   <li><b>缺收件人 / 缺模板不是错误</b>：跳过并留 WARN，
 *       事件消费照常确认——通知缺失可从运营任务列表察觉，MQ 死信堆积更难排查；</li>
 *   <li><b>变量只放业务编号与脱敏字段</b>：载荷由事件方保证（BookingEventPublisher 注释），
 *       渲染层再拦一道（TemplateRenderer）。</li>
 * </ul>
 */
@Service
@RequiresPersistence
public class NotificationTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationTaskService.class);

    /** 站内通知渠道常量（首版唯一渠道）。 */
    private static final String CHANNEL_IN_APP = "IN_APP";

    /** 任务编号序列与前缀。 */
    private static final String TASK_NO_SEQ_KEY = "NOTIFICATION_TASK";
    private static final String TASK_NO_PREFIX = "NTK";

    private final NotificationTaskMapper taskMapper;

    private final NotificationTemplateMapper templateMapper;

    private final NotificationRecipientResolver recipientResolver;

    private final BusinessNoGenerator businessNoGenerator;

    private final ObjectMapper objectMapper;

    public NotificationTaskService(NotificationTaskMapper taskMapper,
                                   NotificationTemplateMapper templateMapper,
                                   NotificationRecipientResolver recipientResolver,
                                   BusinessNoGenerator businessNoGenerator,
                                   ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.templateMapper = templateMapper;
        this.recipientResolver = recipientResolver;
        this.businessNoGenerator = businessNoGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 为一个已确认消费的领域事件创建通知任务。
     *
     * @param eventType     事件类型（如 BOOKING_PAID）
     * @param sourceEventId 事件信封 eventId（去重键来源）
     * @param aggregateId   聚合业务编号（如订单号），日志用
     * @param payload       事件载荷
     * @return 新建的任务编号列表（全部命中去重时为空列表）
     */
    @Transactional
    public List<String> createTasksForEvent(String eventType, String sourceEventId,
                                            String aggregateId, Map<String, Object> payload) {
        if (!NotificationEventTypes.supports(eventType)) {
            // 消费者已过滤，这里兜底：未登记的事件不生成任务也不报错
            LOG.debug("事件类型不受支持，跳过建任务 eventType={} aggregateId={}", eventType, aggregateId);
            return List.of();
        }
        Map<String, Object> normalizedPayload = payload == null ? Map.of() : payload;
        Map<String, Object> variables = templateVariables(normalizedPayload);
        List<String> created = new ArrayList<>(2);
        for (NotificationRecipientResolver.ResolvedRecipient recipient
                : recipientResolver.resolve(eventType, normalizedPayload)) {
            NotificationTemplateEntity template = templateMapper.selectActive(
                    eventType, recipient.audience().name(), CHANNEL_IN_APP);
            if (template == null) {
                LOG.warn("事件缺少可用模板，跳过收件人 eventType={} audience={} aggregateId={}",
                        eventType, recipient.audience(), aggregateId);
                continue;
            }
            String taskNo = createTask(eventType, sourceEventId, template, recipient,
                    variables, aggregateId);
            if (taskNo != null) {
                created.add(taskNo);
            }
        }
        return created;
    }

    /**
     * 运营人工重试失败任务：FAILED → PENDING，立即参与下一轮调度。
     *
     * @param taskNo 任务编号
     * @return 重试后的任务视图
     * @throws BusinessException 任务不存在 / 已取消 / 状态不是 FAILED
     */
    @Transactional
    public NotificationTaskView retryTask(String taskNo) {
        NotificationTaskEntity task = taskMapper.selectByTaskNo(taskNo);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOTIFICATION_TASK_NOT_FOUND);
        }
        if ("CANCELED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.NOTIFICATION_TASK_CANCELED);
        }
        if (taskMapper.requeueFailed(taskNo) == 0) {
            // 条件更新没命中：只有"状态不是 FAILED"一种可能（并发重试会命中其中一次）
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只有失败的任务可以重试");
        }
        return toView(taskMapper.selectByTaskNo(taskNo));
    }

    /**
     * 运营任务分页。
     *
     * @param status 状态过滤，可空
     * @param page   页码
     * @param size   条数
     * @return 分页任务
     */
    @Transactional(readOnly = true)
    public PageResult<NotificationTaskView> operatorPage(String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<NotificationTaskEntity> wrapper = new LambdaQueryWrapper<NotificationTaskEntity>()
                .eq(status != null && !status.isBlank(),
                        NotificationTaskEntity::getStatus, status)
                .orderByDesc(NotificationTaskEntity::getId);
        Page<NotificationTaskEntity> result =
                taskMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toView).toList(),
                safePage, safeSize, result.getTotal());
    }

    /**
     * 单个收件人的任务落库；去重键冲突视为"事件已处理过"，返回 null。
     */
    private String createTask(String eventType, String sourceEventId,
                              NotificationTemplateEntity template,
                              NotificationRecipientResolver.ResolvedRecipient recipient,
                              Map<String, Object> variables, String aggregateId) {
        NotificationTaskEntity task = new NotificationTaskEntity();
        task.setTaskNo(businessNoGenerator.next(TASK_NO_SEQ_KEY, TASK_NO_PREFIX));
        task.setSourceEventId(sourceEventId);
        task.setEventType(eventType);
        task.setTemplateCode(template.getTemplateCode());
        task.setRecipientAccountId(recipient.accountId());
        task.setRecipientRole(recipient.audience().name());
        task.setChannel(CHANNEL_IN_APP);
        task.setPayloadJson(writeJson(variables));
        task.setDedupeKey(sourceEventId + ":" + recipient.accountId()
                + ":" + template.getTemplateCode() + ":" + CHANNEL_IN_APP);
        task.setStatus("PENDING");
        task.setPriority(template.getPriority());
        task.setRetryCount(0);
        task.setMaxRetryCount(5);
        task.setScheduledAt(java.time.LocalDateTime.now());
        try {
            taskMapper.insert(task);
            return task.getTaskNo();
        } catch (DuplicateKeyException exception) {
            // 同一事件 + 收件人 + 模板的任务已存在：重复消费，幂等跳过
            LOG.info("通知任务已存在，跳过 dedupeKey 任务归属 aggregateId={} template={}",
                    aggregateId, template.getTemplateCode());
            return null;
        }
    }

    /**
     * 载荷 → 模板变量：补充 serviceDate（改期事件优先取新日期）与 startTime
     * （LocalDateTime 的 'T' 换成空格），其余键原样透传。
     */
    private Map<String, Object> templateVariables(Map<String, Object> payload) {
        Map<String, Object> variables = new LinkedHashMap<>(payload);
        Object serviceDate = variables.containsKey("newDate")
                ? variables.get("newDate") : variables.get("appointmentDate");
        if (serviceDate != null) {
            variables.put("serviceDate", serviceDate);
        }
        Object start = variables.get("appointmentStart");
        if (start != null) {
            variables.put("startTime", String.valueOf(start).replace('T', ' '));
        }
        return variables;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            // 载荷来自 JSON 反序列化，再序列化失败只能是编码错误，直接让事务回滚
            throw new IllegalStateException("通知任务载荷序列化失败", exception);
        }
    }

    private NotificationTaskView toView(NotificationTaskEntity task) {
        return new NotificationTaskView(task.getTaskNo(), task.getSourceEventId(),
                task.getEventType(), task.getTemplateCode(), task.getRecipientAccountId(),
                task.getRecipientRole(), task.getChannel(), task.getStatus(),
                task.getRetryCount(), task.getMaxRetryCount(), task.getNextRetryAt(),
                task.getLastError(), task.getSentAt(), task.getCreatedAt());
    }
}
