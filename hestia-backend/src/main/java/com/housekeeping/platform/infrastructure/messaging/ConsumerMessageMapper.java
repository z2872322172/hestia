package com.housekeeping.platform.infrastructure.messaging;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code consumer_message} 数据访问。
 */
@Mapper
public interface ConsumerMessageMapper extends BaseMapper<ConsumerMessage> {

    /**
     * 按消费者组与消息 ID 查询幂等记录。
     *
     * @param consumerGroup 消费者组
     * @param messageId     消息 ID
     * @return 幂等记录；不存在时返回 {@code null}
     */
    @Select("""
            SELECT * FROM consumer_message
             WHERE consumer_group = #{consumerGroup}
               AND message_id = #{messageId}
             LIMIT 1
            """)
    ConsumerMessage selectByGroupAndMessageId(@Param("consumerGroup") String consumerGroup,
                                              @Param("messageId") String messageId);
}
