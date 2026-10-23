package com.housekeeping.platform.scheduling.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.scheduling.persistence.entity.WorkerScheduleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 人员排班 Mapper。
 *
 * <p>同一人员同一日期同一时间窗的唯一性由 {@code uk_worker_schedule} 唯一索引兜底，
 * 应用层先查后插只是为了给出更友好的冲突提示，并发冲突仍以数据库约束为准。</p>
 */
@Mapper
public interface WorkerScheduleMapper extends BaseMapper<WorkerScheduleEntity> {

    /**
     * 查询人员在日期区间内的排班（含已取消，供本人与运营视角）。
     *
     * @param workerId 人员 ID
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @return 排班列表，按日期、窗口 ID 排序
     */
    @Select("SELECT * FROM worker_schedule WHERE worker_id = #{workerId} "
            + "AND service_date BETWEEN #{dateFrom} AND #{dateTo} "
            + "ORDER BY service_date ASC, window_id ASC")
    List<WorkerScheduleEntity> selectByWorkerAndDateRange(@Param("workerId") Long workerId,
                                                          @Param("dateFrom") LocalDate dateFrom,
                                                          @Param("dateTo") LocalDate dateTo);

    /**
     * 查询日期区间内的全部排班（运营视角）。
     *
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @return 排班列表
     */
    @Select("SELECT * FROM worker_schedule WHERE service_date BETWEEN #{dateFrom} AND #{dateTo} "
            + "ORDER BY service_date ASC, worker_id ASC, window_id ASC")
    List<WorkerScheduleEntity> selectByDateRange(@Param("dateFrom") LocalDate dateFrom,
                                                 @Param("dateTo") LocalDate dateTo);
}
