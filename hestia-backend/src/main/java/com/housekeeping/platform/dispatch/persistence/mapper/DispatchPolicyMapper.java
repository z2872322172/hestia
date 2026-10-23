package com.housekeeping.platform.dispatch.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchPolicyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 派单评分策略 Mapper。
 */
@Mapper
public interface DispatchPolicyMapper extends BaseMapper<DispatchPolicyEntity> {

    /**
     * 查询当前生效策略（应用层保证同一时刻最多一个 ACTIVE）。
     *
     * @return ACTIVE 策略或 null
     */
    @Select("SELECT * FROM dispatch_policy WHERE status = 'ACTIVE' "
            + "ORDER BY id DESC LIMIT 1")
    DispatchPolicyEntity selectActive();
}
