package com.housekeeping.platform.infrastructure.persistence.idempotency;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 接口幂等记录 Mapper。
 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordEntity> {

    /**
     * 按幂等键查询。
     *
     * @param idempotencyKey 客户端幂等键
     * @return 记录或 null
     */
    @Select("SELECT * FROM idempotency_record WHERE idempotency_key = #{idempotencyKey}")
    IdempotencyRecordEntity selectByKey(@Param("idempotencyKey") String idempotencyKey);
}
