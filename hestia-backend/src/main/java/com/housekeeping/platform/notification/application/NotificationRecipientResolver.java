package com.housekeeping.platform.notification.application;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.application.contract.CustomerQueryService;
import com.housekeeping.platform.notification.domain.NotificationAudience;
import com.housekeeping.platform.notification.domain.NotificationEventTypes;
import com.housekeeping.platform.workforce.application.WorkerProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通知收件人解析器：事件类型 + 事件载荷 → 收件人列表。
 *
 * <p>解析失败（客户档案已关闭、人员已删除）<b>跳过该收件人而不是让事件消费失败</b>：
 * 通知是业务副产物，收件人缺失不能阻断 MQ 确认，否则同一条事件会被反复重投。
 * 跳过时打 WARN 留痕，运营侧可从任务列表的缺口察觉。</p>
 *
 * <p>收件人映射（docs/modules/12 第 4、16 节）：</p>
 * <ul>
 *   <li>CUSTOMER：所有订单事件都通知下单客户（载荷 customerId）；</li>
 *   <li>WORKER：支付（新任务）、改期（新人员）、取消与完成（当前人员）通知人员，
 *       载荷里没有人员 ID 时（如未激活派单就取消）自然没有人员侧通知。</li>
 * </ul>
 */
@Component
@RequiresPersistence
public class NotificationRecipientResolver {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationRecipientResolver.class);

    private final CustomerQueryService customerQueryService;

    private final WorkerProfileService workerProfileService;

    public NotificationRecipientResolver(CustomerQueryService customerQueryService,
                                         WorkerProfileService workerProfileService) {
        this.customerQueryService = customerQueryService;
        this.workerProfileService = workerProfileService;
    }

    /**
     * 解析事件的收件人。
     *
     * @param eventType 事件类型
     * @param payload   事件载荷（JSON 反序列化后的 Map）
     * @return 收件人列表（可能为空，如下单事件没有人员侧）
     */
    public List<ResolvedRecipient> resolve(String eventType, Map<String, Object> payload) {
        List<ResolvedRecipient> recipients = new ArrayList<>(2);
        Long customerId = longValue(payload.get("customerId"));
        String customerNo = textValue(payload.get("customerNo"));
        if (customerId != null || customerNo != null) {
            // 正常链路载荷带内部 customerId；冒烟链路只拿得到公开业务编号，
            // 缺省时回退 customerNo。两条路都收敛到"客户档案 → 账号 ID"
            try {
                long accountId = customerId != null
                        ? customerQueryService.requireAccountIdByCustomerId(customerId)
                        : customerQueryService.requireAccountIdByCustomerNo(customerNo);
                recipients.add(new ResolvedRecipient(NotificationAudience.CUSTOMER, accountId));
            } catch (RuntimeException exception) {
                LOG.warn("订单事件客户收件人解析失败，跳过 customerId={} customerNo={} eventType={} 原因={}",
                        customerId, customerNo, eventType, exception.getMessage());
            }
        }
        Long workerId = resolveWorkerId(eventType, payload);
        if (workerId != null) {
            try {
                long accountId = workerProfileService.requireAccountIdByWorkerId(workerId);
                recipients.add(new ResolvedRecipient(NotificationAudience.WORKER, accountId));
            } catch (RuntimeException exception) {
                LOG.warn("订单事件人员收件人解析失败，跳过 workerId={} eventType={} 原因={}",
                        workerId, eventType, exception.getMessage());
            }
        }
        return recipients;
    }

    /**
     * 事件里的人员 ID：改期优先取新人员，其余取当前人员。
     */
    private Long resolveWorkerId(String eventType, Map<String, Object> payload) {
        Long workerId = NotificationEventTypes.BOOKING_RESCHEDULED.equals(eventType)
                ? longValue(payload.get("newWorkerId"))
                : null;
        return workerId != null ? workerId : longValue(payload.get("currentWorkerId"));
    }

    /** JSON 数字统一转 Long；字符串形式的数字也容忍（冒烟脚本可能直接传字符串）。 */
    private Long longValue(Object value) {
        if (value instanceof Number number) {
            long id = number.longValue();
            return id > 0 ? id : null;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                long id = Long.parseLong(text.trim());
                return id > 0 ? id : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 非空字符串取出并去空白，其余（数字、嵌套结构）一律视为无效。 */
    private String textValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    /**
     * 解析出的收件人：受众决定模板选择，账号 ID 决定任务归属。
     */
    public record ResolvedRecipient(NotificationAudience audience, long accountId) {
    }
}
