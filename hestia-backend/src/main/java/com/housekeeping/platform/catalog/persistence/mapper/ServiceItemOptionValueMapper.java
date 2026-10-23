package com.housekeeping.platform.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionValueEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 服务规格值 Mapper。
 */
@Mapper
public interface ServiceItemOptionValueMapper extends BaseMapper<ServiceItemOptionValueEntity> {

    /**
     * 批量查询多个规格组下的有效规格值。
     *
     * <p>一次查完而不是循环单查：详情页通常有 3~6 个规格组，
     * 循环查询就是 3~6 次往返，在列表页会被放大成 N+1。</p>
     *
     * @param optionIds 规格组 ID 集合
     * @return 规格值列表
     */
    @Select("""
            <script>
            SELECT * FROM service_item_option_value
            WHERE status = 'ACTIVE' AND option_id IN
            <foreach collection="optionIds" item="optionId" open="(" separator="," close=")">
                #{optionId}
            </foreach>
            ORDER BY option_id ASC, sort_order ASC, id ASC
            </script>
            """)
    List<ServiceItemOptionValueEntity> selectActiveByOptionIds(@Param("optionIds") Collection<Long> optionIds);

    /**
     * 删除多个规格组下的全部规格值（改规格时整体重建）。
     *
     * @param optionIds 规格组 ID 集合
     * @return 影响行数
     */
    @Delete("""
            <script>
            DELETE FROM service_item_option_value WHERE option_id IN
            <foreach collection="optionIds" item="optionId" open="(" separator="," close=")">
                #{optionId}
            </foreach>
            </script>
            """)
    int deleteByOptionIds(@Param("optionIds") Collection<Long> optionIds);
}
