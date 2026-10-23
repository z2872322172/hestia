package com.housekeeping.platform.scheduling.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.scheduling.persistence.entity.ServiceWindowEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标准服务时间窗 Mapper。
 *
 * <p>时间窗数量少且变化低频，基础 CRUD 足够；重叠校验在应用层用
 * {@code WindowTimeline} 完成后整体写入。</p>
 */
@Mapper
public interface ServiceWindowMapper extends BaseMapper<ServiceWindowEntity> {
}
