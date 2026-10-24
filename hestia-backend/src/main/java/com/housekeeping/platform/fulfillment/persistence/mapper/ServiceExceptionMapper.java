package com.housekeeping.platform.fulfillment.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceExceptionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 履约异常 Mapper。
 *
 * <p>异常处理条件更新（OPEN / ACKNOWLEDGED → RESOLVED / REJECTED），
 * 影响行数 0 即已被并发处理（docs/modules/09 第 10 节：处理结果必须写处理人与时间）。</p>
 */
@Mapper
public interface ServiceExceptionMapper extends BaseMapper<ServiceExceptionEntity> {

    /**
     * 履约记录下的异常列表（上报时间倒序）。
     *
     * @param serviceRecordId 履约记录 ID
     * @return 异常列表
     */
    @Select("SELECT * FROM service_exception "
            + "WHERE service_record_id = #{serviceRecordId} ORDER BY id DESC")
    List<ServiceExceptionEntity> selectByRecordId(@Param("serviceRecordId") Long serviceRecordId);

    /**
     * 记录下未处理的高优先级安全异常数（开始服务阻断用，docs 第 7.3 节）。
     *
     * @param serviceRecordId 履约记录 ID
     * @return 未处理安全异常数
     */
    @Select("SELECT COUNT(*) FROM service_exception "
            + "WHERE service_record_id = #{serviceRecordId} "
            + "AND exception_type = 'SAFETY_RISK' AND status IN ('OPEN', 'ACKNOWLEDGED')")
    long countOpenSafety(@Param("serviceRecordId") Long serviceRecordId);

    /**
     * 按异常单号查询。
     *
     * @param exceptionNo 异常单号
     * @return 异常或 null
     */
    @Select("SELECT * FROM service_exception WHERE exception_no = #{exceptionNo}")
    ServiceExceptionEntity selectByExceptionNo(@Param("exceptionNo") String exceptionNo);

    /**
     * 运营处理异常：OPEN / ACKNOWLEDGED → RESOLVED / REJECTED，写处理人、结论与时间。
     *
     * @param exceptionId 异常 ID
     * @param status      目标状态 RESOLVED / REJECTED
     * @param resolution  处理结论
     * @param accountId   处理人账号 ID
     * @return 影响行数，0 表示已处理
     */
    @Update("UPDATE service_exception SET status = #{status}, resolution = #{resolution}, "
            + "handled_by_account_id = #{accountId}, resolved_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE id = #{exceptionId} "
            + "AND status IN ('OPEN', 'ACKNOWLEDGED')")
    int handle(@Param("exceptionId") Long exceptionId, @Param("status") String status,
               @Param("resolution") String resolution, @Param("accountId") Long accountId);
}
