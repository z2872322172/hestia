package com.housekeeping.platform.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.catalog.persistence.entity.ServiceCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务分类 Mapper。
 */
@Mapper
public interface ServiceCategoryMapper extends BaseMapper<ServiceCategoryEntity> {

    /**
     * 按对外编号查询。
     *
     * @param categoryNo 分类编号
     * @return 分类；不存在返回 {@code null}
     */
    @Select("SELECT * FROM service_category WHERE category_no = #{categoryNo} LIMIT 1")
    ServiceCategoryEntity selectByCategoryNo(@Param("categoryNo") String categoryNo);

    /**
     * 查询子分类。
     *
     * @param parentId 父分类 ID
     * @return 子分类列表
     */
    @Select("SELECT * FROM service_category WHERE parent_id = #{parentId} ORDER BY sort_order ASC, id ASC")
    List<ServiceCategoryEntity> selectChildren(@Param("parentId") Long parentId);

    /**
     * 查询全部分类（运营后台用）。
     *
     * @return 分类列表
     */
    @Select("SELECT * FROM service_category ORDER BY parent_id IS NULL DESC, sort_order ASC, id ASC")
    List<ServiceCategoryEntity> selectAllOrdered();

    /**
     * 查询前台可见分类：自身 ACTIVE，且父链上的分类也 ACTIVE。
     *
     * <p>用一条 SQL 做自连接而不是在 Java 里过滤：三级树的过滤如果放到 Java，
     * 就会出现"父分类停用但子分类还显示"这种半截状态。</p>
     *
     * @return 可见分类列表
     */
    @Select("""
            SELECT c.* FROM service_category c
            LEFT JOIN service_category p ON p.id = c.parent_id
            LEFT JOIN service_category g ON g.id = p.parent_id
            WHERE c.status = 'ACTIVE'
              AND (c.parent_id IS NULL OR p.status = 'ACTIVE')
              AND (p.parent_id IS NULL OR g.status = 'ACTIVE')
            ORDER BY COALESCE(c.parent_id, c.id) ASC, c.parent_id IS NOT NULL ASC, c.sort_order ASC, c.id ASC
            """)
    List<ServiceCategoryEntity> selectVisible();

    /**
     * 统计某分类下未下架的服务项目数量。
     *
     * <p>停用分类前要看这个数：如果还有在售项目挂在下面，停用会让它们在前台凭空消失。</p>
     *
     * @param categoryId 分类 ID
     * @return 未下架服务项目数量
     */
    @Select("SELECT COUNT(*) FROM service_item WHERE category_id = #{categoryId} AND status <> 'OFFLINE'")
    int countActiveItems(@Param("categoryId") Long categoryId);
}
