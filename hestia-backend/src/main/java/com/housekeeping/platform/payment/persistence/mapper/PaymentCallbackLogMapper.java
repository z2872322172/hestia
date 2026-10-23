package com.housekeeping.platform.payment.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.payment.persistence.entity.PaymentCallbackLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 支付回调日志 Mapper。
 *
 * <p>幂等协议：先插 {@code RECEIVED} 占位行抢占 {@code (channel, callback_id)}，
 * 插入冲突即重复回调；处理完成后回写终态结果。</p>
 */
@Mapper
public interface PaymentCallbackLogMapper extends BaseMapper<PaymentCallbackLogEntity> {

    /**
     * 回写回调处理结果。
     *
     * @param id        日志 ID
     * @param result    终态结果（SUCCESS / DUPLICATE / FAILED）
     * @param errorMessage 失败原因，可空
     * @return 影响行数
     */
    @Update("UPDATE payment_callback_log SET result = #{result}, "
            + "error_message = #{errorMessage}, processed_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND result = 'RECEIVED'")
    int finish(@Param("id") Long id,
               @Param("result") String result,
               @Param("errorMessage") String errorMessage);

    /**
     * 按渠道与回调 ID 查询（补偿任务判重用）。
     *
     * @param channel    渠道
     * @param callbackId 回调 ID
     * @return 日志或 null
     */
    @Select("SELECT * FROM payment_callback_log "
            + "WHERE channel = #{channel} AND callback_id = #{callbackId}")
    PaymentCallbackLogEntity selectByCallbackId(@Param("channel") String channel,
                                                @Param("callbackId") String callbackId);
}
