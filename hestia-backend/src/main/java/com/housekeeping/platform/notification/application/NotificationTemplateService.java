package com.housekeeping.platform.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.notification.application.view.NotificationTemplateView;
import com.housekeeping.platform.notification.domain.TemplateRenderer;
import com.housekeeping.platform.notification.persistence.entity.NotificationTemplateEntity;
import com.housekeeping.platform.notification.persistence.mapper.NotificationTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 通知模板维护服务（运营端，docs/modules/12 第 15.3 节）。
 *
 * <p>模板编码是任务外键（notification_task.template_code → notification_template），
 * 因此<b>不允许新增/删除编码，只允许改内容与启停</b>：删模板会让历史任务的外键悬空，
 * 新编码留给迁移种子而不是运行期接口。</p>
 */
@Service
@RequiresPersistence
public class NotificationTemplateService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final NotificationTemplateMapper templateMapper;

    public NotificationTemplateService(NotificationTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    /**
     * 全量模板列表（数量小，不翻页）。
     *
     * @return 模板视图列表
     */
    @Transactional(readOnly = true)
    public List<NotificationTemplateView> list() {
        return templateMapper.selectList(new LambdaQueryWrapper<NotificationTemplateEntity>()
                        .orderByAsc(NotificationTemplateEntity::getEventType)
                        .orderByAsc(NotificationTemplateEntity::getAudience))
                .stream().map(this::toView).toList();
    }

    /**
     * 修改模板内容或启停。内容变更时内容版本自增 1。
     *
     * <p>敏感变量在保存时前置拦截（渲染时还会再拦一次）：
     * 双重防线保证"模板被改坏"最多造成发送失败，不会造成泄露。</p>
     *
     * @param templateCode 模板编码
     * @param request      修改请求（字段可空 = 保持原值）
     * @return 修改后的模板视图
     */
    @Transactional
    public NotificationTemplateView update(String templateCode, TemplateUpdateRequest request) {
        NotificationTemplateEntity entity = templateMapper.selectByCode(templateCode);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOTIFICATION_TEMPLATE_NOT_FOUND);
        }
        String newTitle = request.titleTemplate() == null
                ? entity.getTitleTemplate() : request.titleTemplate();
        String newContent = request.contentTemplate() == null
                ? entity.getContentTemplate() : request.contentTemplate();
        if (!StringUtils.hasText(newTitle) || !StringUtils.hasText(newContent)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模板标题与正文不能为空");
        }
        String forbidden = TemplateRenderer.findForbiddenVariable(newTitle);
        if (forbidden == null) {
            forbidden = TemplateRenderer.findForbiddenVariable(newContent);
        }
        if (forbidden != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "模板不能引用敏感变量 {" + forbidden + "}");
        }
        String newStatus = request.status() == null ? entity.getStatus() : request.status();
        if (!STATUS_ACTIVE.equals(newStatus) && !STATUS_DISABLED.equals(newStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模板状态只允许 ACTIVE / DISABLED");
        }
        LambdaUpdateWrapper<NotificationTemplateEntity> update =
                new LambdaUpdateWrapper<NotificationTemplateEntity>()
                        .eq(NotificationTemplateEntity::getTemplateCode, templateCode)
                        .set(NotificationTemplateEntity::getTitleTemplate, newTitle)
                        .set(NotificationTemplateEntity::getContentTemplate, newContent)
                        .set(NotificationTemplateEntity::getStatus, newStatus)
                        .set(NotificationTemplateEntity::getVersion, entity.getVersion() + 1);
        if (templateMapper.update(null, update) == 0) {
            // 模板编码是主键，更新未命中只可能是并发删除（当前不存在删除入口），防御性处理
            throw new BusinessException(ErrorCode.NOTIFICATION_TEMPLATE_NOT_FOUND);
        }
        return toView(templateMapper.selectByCode(templateCode));
    }

    /**
     * 模板修改请求：字段为 null 表示保持原值。
     *
     * @param titleTemplate   标题模板
     * @param contentTemplate 正文模板
     * @param status          ACTIVE / DISABLED
     */
    public record TemplateUpdateRequest(String titleTemplate,
                                        String contentTemplate,
                                        String status) {
    }

    private NotificationTemplateView toView(NotificationTemplateEntity entity) {
        return new NotificationTemplateView(entity.getTemplateCode(), entity.getTemplateName(),
                entity.getEventType(), entity.getAudience(), entity.getChannel(),
                entity.getTitleTemplate(), entity.getContentTemplate(), entity.getPriority(),
                entity.getStatus(), entity.getVersion(), entity.getUpdatedAt());
    }
}
