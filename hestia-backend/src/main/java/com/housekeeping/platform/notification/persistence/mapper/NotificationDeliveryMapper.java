package com.housekeeping.platform.notification.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.notification.persistence.entity.NotificationDeliveryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知发送记录 Mapper（只追加，无自定义 SQL）。
 */
@Mapper
public interface NotificationDeliveryMapper extends BaseMapper<NotificationDeliveryEntity> {
}
