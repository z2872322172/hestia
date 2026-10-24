package com.housekeeping.platform.aftersales.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 售后工单时间线 Mapper。
 */
@Mapper
public interface AfterSaleEventMapper extends BaseMapper<AfterSaleEventEntity> {

    /**
     * 查询工单时间线（时间升序）。
     *
     * @param ticketId 工单 ID
     * @return 事件列表
     */
    @Select("SELECT * FROM after_sale_event WHERE ticket_id = #{ticketId} ORDER BY id")
    List<AfterSaleEventEntity> selectByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 工单是否已有某类事件（SLA 标记等一次性动作的幂等依据）。
     *
     * @param ticketId  工单 ID
     * @param eventType 事件类型
     * @return 是否存在
     */
    @Select("SELECT COUNT(*) FROM after_sale_event WHERE ticket_id = #{ticketId} "
            + "AND event_type = #{eventType}")
    boolean existsEvent(@Param("ticketId") long ticketId, @Param("eventType") String eventType);
}
