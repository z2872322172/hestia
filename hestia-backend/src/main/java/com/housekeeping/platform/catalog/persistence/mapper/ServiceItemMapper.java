package com.housekeeping.platform.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 服务项目 Mapper。
 */
@Mapper
public interface ServiceItemMapper extends BaseMapper<ServiceItemEntity> {

    /**
     * 按对外编号查询（不限状态，运营后台用）。
     *
     * @param itemNo 服务项目编号
     * @return 服务项目；不存在返回 {@code null}
     */
    @Select("SELECT * FROM service_item WHERE item_no = #{itemNo} LIMIT 1")
    ServiceItemEntity selectByItemNo(@Param("itemNo") String itemNo);

    /**
     * 按对外编号查询<b>已发布</b>的服务项目（前台用）。
     *
     * <p>把状态条件写进 SQL 而不是查出来再判断：草稿和已下架项目对消费者来说
     * 应当与"不存在"完全等价，连"存在但不可买"这个信息都不该泄露。</p>
     *
     * @param itemNo 服务项目编号
     * @return 已发布的服务项目；不存在或不可售返回 {@code null}
     */
    @Select("SELECT * FROM service_item WHERE item_no = #{itemNo} AND status = 'PUBLISHED' LIMIT 1")
    ServiceItemEntity selectPublishedByItemNo(@Param("itemNo") String itemNo);

    /**
     * 查询某分类下的已发布服务项目。
     *
     * @param categoryId 分类 ID
     * @return 服务项目列表
     */
    @Select("SELECT * FROM service_item WHERE category_id = #{categoryId} AND status = 'PUBLISHED' "
            + "ORDER BY published_at DESC, id DESC")
    List<ServiceItemEntity> selectPublishedByCategory(@Param("categoryId") Long categoryId);

    /**
     * 查询全部已发布服务项目。
     *
     * @return 服务项目列表
     */
    @Select("SELECT * FROM service_item WHERE status = 'PUBLISHED' "
            + "ORDER BY published_at DESC, id DESC")
    List<ServiceItemEntity> selectAllPublished();

    /**
     * 发布：只有 DRAFT 或 OFFLINE 能迁到 PUBLISHED。
     *
     * <p>用条件更新而不是"先查后改"：两个运营同时点发布时，
     * 只有一条能改到 1 行，另一条拿到 0 行就知道自己晚了。</p>
     *
     * @param id 服务项目 ID
     * @return 影响行数，0 表示状态已变
     */
    @Update("UPDATE service_item SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(3), "
            + "offline_at = NULL, version = version + 1 "
            + "WHERE id = #{id} AND status IN ('DRAFT', 'OFFLINE')")
    int publish(@Param("id") Long id);

    /**
     * 下架：只有 PUBLISHED 能迁到 OFFLINE。
     *
     * @param id 服务项目 ID
     * @return 影响行数，0 表示状态已变
     */
    @Update("UPDATE service_item SET status = 'OFFLINE', offline_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE id = #{id} AND status = 'PUBLISHED'")
    int offline(@Param("id") Long id);
}
