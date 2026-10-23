package com.housekeeping.platform.location.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.location.persistence.entity.ServiceAreaCoverageEntity;
import com.housekeeping.platform.location.persistence.row.MatchCandidateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务区域覆盖 Mapper。
 */
@Mapper
public interface ServiceAreaCoverageMapper extends BaseMapper<ServiceAreaCoverageEntity> {

    /**
     * 查询地址可能命中的覆盖记录。
     *
     * <p>过滤条件全部压在 SQL 里而不是取回内存再筛：</p>
     * <ul>
     *   <li>覆盖 ACTIVE 且服务区域 ACTIVE —— 草稿、停用、归档一律不参与匹配；</li>
     *   <li>服务区域属于该城市 —— 防止跨城市误配；</li>
     *   <li>生效时间窗口 —— 未生效或已过期的配置不能命中。</li>
     * </ul>
     *
     * <p>排序不在这里做：排序与冲突判定是纯领域规则，放在
     * {@code MatchRanking} 里才能被单测穷举，也避免"数据库排序 + Java 排序"两套规则打架。</p>
     *
     * @param cityRegionId 城市行政区 ID
     * @param regionIds    区县与街道的行政区 ID 列表，不能为空
     * @return 候选行
     */
    @Select("""
            <script>
            SELECT sa.id            AS service_area_id,
                   sa.area_no       AS area_no,
                   sa.area_name     AS area_name,
                   c.coverage_level AS coverage_level,
                   sa.priority      AS priority,
                   sa.effective_from AS effective_from,
                   r.region_code    AS matched_region_code
            FROM service_area_coverage c
                     JOIN service_area sa ON sa.id = c.service_area_id
                     JOIN administrative_region r ON r.id = c.region_id
            WHERE c.status = 'ACTIVE'
              AND sa.status = 'ACTIVE'
              AND sa.city_region_id = #{cityRegionId}
              AND c.region_id IN
              <foreach collection="regionIds" item="regionId" open="(" separator="," close=")">
                  #{regionId}
              </foreach>
              AND (sa.effective_from IS NULL OR sa.effective_from &lt;= CURRENT_TIMESTAMP(3))
              AND (sa.effective_to IS NULL OR sa.effective_to &gt; CURRENT_TIMESTAMP(3))
            </script>
            """)
    List<MatchCandidateRow> selectMatchCandidates(@Param("cityRegionId") Long cityRegionId,
                                                  @Param("regionIds") List<Long> regionIds);

    /**
     * 查询服务区域的全部覆盖记录（含停用），供运营查看配置历史。
     *
     * @param serviceAreaId 服务区域 ID
     * @return 覆盖记录
     */
    @Select("SELECT * FROM service_area_coverage WHERE service_area_id = #{serviceAreaId} ORDER BY id ASC")
    List<ServiceAreaCoverageEntity> selectByServiceAreaId(@Param("serviceAreaId") Long serviceAreaId);

    /**
     * 查询某服务区域是否已覆盖某行政区。
     *
     * @param serviceAreaId 服务区域 ID
     * @param regionId      行政区 ID
     * @return 覆盖记录；不存在返回 {@code null}
     */
    @Select("SELECT * FROM service_area_coverage WHERE service_area_id = #{serviceAreaId} "
            + "AND region_id = #{regionId} LIMIT 1")
    ServiceAreaCoverageEntity selectByAreaAndRegion(@Param("serviceAreaId") Long serviceAreaId,
                                                    @Param("regionId") Long regionId);

    /**
     * 统计服务区域的有效覆盖数量。
     *
     * @param serviceAreaId 服务区域 ID
     * @return 有效覆盖数
     */
    @Select("SELECT COUNT(*) FROM service_area_coverage WHERE service_area_id = #{serviceAreaId} "
            + "AND status = 'ACTIVE'")
    long countActiveByServiceAreaId(@Param("serviceAreaId") Long serviceAreaId);
}
