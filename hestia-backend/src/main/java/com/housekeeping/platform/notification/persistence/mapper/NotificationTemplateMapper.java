package com.housekeeping.platform.notification.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.notification.persistence.entity.NotificationTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 通知模板 Mapper。
 *
 * <p>模板查询按键（事件类型 + 受众 + 渠道 + ACTIVE）进行：
 * 渲染阶段找不到 ACTIVE 模板属于任务级失败，不抛异常。</p>
 */
@Mapper
public interface NotificationTemplateMapper extends BaseMapper<NotificationTemplateEntity> {

    /**
     * 按模板选择键取启用中的模板。
     *
     * @param eventType 事件类型
     * @param audience  受众
     * @param channel   渠道
     * @return 模板或 null
     */
    @Select("SELECT * FROM notification_template "
            + "WHERE event_type = #{eventType} AND audience = #{audience} "
            + "AND channel = #{channel} AND status = 'ACTIVE' "
            + "ORDER BY version DESC LIMIT 1")
    NotificationTemplateEntity selectActive(@Param("eventType") String eventType,
                                            @Param("audience") String audience,
                                            @Param("channel") String channel);

    /**
     * 按模板编码查询（运营端展示与修改）。
     *
     * @param templateCode 模板编码
     * @return 模板或 null
     */
    @Select("SELECT * FROM notification_template WHERE template_code = #{templateCode}")
    NotificationTemplateEntity selectByCode(@Param("templateCode") String templateCode);
}
