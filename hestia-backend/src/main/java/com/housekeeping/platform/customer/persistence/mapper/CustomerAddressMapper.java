package com.housekeeping.platform.customer.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.customer.persistence.entity.CustomerAddressEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 客户地址 Mapper。
 *
 * <p>默认地址相关的三条写操作（清旧、设新、提升）都写成单条 SQL：
 * "先查出实体、改字段、再 updateById"在并发下会互相覆盖，
 * 而"同一客户最多一个有效默认地址"是数据库唯一索引在兜底的硬约束。</p>
 */
@Mapper
public interface CustomerAddressMapper extends BaseMapper<CustomerAddressEntity> {

    /**
     * 按地址编号查询（含已删除，供内部追溯）。
     *
     * @param addressNo 地址编号
     * @return 地址；不存在返回 {@code null}
     */
    @Select("SELECT * FROM customer_address WHERE address_no = #{addressNo} LIMIT 1")
    CustomerAddressEntity selectByAddressNo(@Param("addressNo") String addressNo);

    /**
     * 查询客户的有效地址列表。
     *
     * <p>排序与索引 {@code idx_customer_address_list} 对齐：默认地址置顶，
     * 其次按最近使用时间倒序。</p>
     *
     * @param customerId 客户档案 ID
     * @return 地址列表
     */
    @Select("SELECT * FROM customer_address WHERE customer_id = #{customerId} AND status = 'ACTIVE' "
            + "ORDER BY is_default DESC, last_used_at DESC, id DESC")
    List<CustomerAddressEntity> selectActiveByCustomerId(@Param("customerId") Long customerId);

    /**
     * 统计客户有效地址数量。
     *
     * @param customerId 客户档案 ID
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM customer_address WHERE customer_id = #{customerId} AND status = 'ACTIVE'")
    long countActiveByCustomerId(@Param("customerId") Long customerId);

    /**
     * 查询客户当前默认地址。
     *
     * @param customerId 客户档案 ID
     * @return 默认地址；不存在返回 {@code null}
     */
    @Select("SELECT * FROM customer_address WHERE customer_id = #{customerId} "
            + "AND status = 'ACTIVE' AND is_default = 1 LIMIT 1")
    CustomerAddressEntity selectDefault(@Param("customerId") Long customerId);

    /**
     * 清除客户当前的默认标记。
     *
     * @param customerId 客户档案 ID
     * @return 影响行数
     */
    @Update("UPDATE customer_address SET is_default = 0 "
            + "WHERE customer_id = #{customerId} AND is_default = 1 AND status = 'ACTIVE'")
    int clearDefault(@Param("customerId") Long customerId);

    /**
     * 把指定地址设为默认。
     *
     * @param addressId  地址 ID
     * @param customerId 客户档案 ID（同时校验归属，防止越权设置）
     * @return 影响行数
     */
    @Update("UPDATE customer_address SET is_default = 1, last_used_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{addressId} AND customer_id = #{customerId} AND status = 'ACTIVE'")
    int markDefault(@Param("addressId") Long addressId, @Param("customerId") Long customerId);

    /**
     * 查询最近使用的有效地址（删除默认地址后用于提升）。
     *
     * @param customerId  客户档案 ID
     * @param excludeId   需要排除的地址 ID（即将被删除的那条）
     * @return 地址；没有其他有效地址返回 {@code null}
     */
    @Select("SELECT * FROM customer_address WHERE customer_id = #{customerId} AND status = 'ACTIVE' "
            + "AND id <> #{excludeId} ORDER BY last_used_at DESC, id DESC LIMIT 1")
    CustomerAddressEntity selectMostRecent(@Param("customerId") Long customerId,
                                           @Param("excludeId") Long excludeId);

    /**
     * 软删除地址。
     *
     * <p>{@code is_default = 0} 是必须的：数据库有
     * {@code chk_customer_address_deleted_not_default} 约束，
     * 已删除地址不能继续挂着默认标记。</p>
     *
     * @param addressId  地址 ID
     * @param customerId 客户档案 ID
     * @return 影响行数
     */
    @Update("UPDATE customer_address SET status = 'DELETED', is_default = 0, "
            + "deleted_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{addressId} AND customer_id = #{customerId} AND status = 'ACTIVE'")
    int softDelete(@Param("addressId") Long addressId, @Param("customerId") Long customerId);

    /**
     * 更新服务区域匹配结果。
     *
     * @param addressId     地址 ID
     * @param serviceAreaId 服务区域 ID，未匹配时为 {@code null}
     * @param matchStatus   匹配状态
     * @return 影响行数
     */
    @Update("UPDATE customer_address SET service_area_id = #{serviceAreaId}, "
            + "area_match_status = #{matchStatus}, area_matched_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{addressId}")
    int updateAreaMatch(@Param("addressId") Long addressId,
                        @Param("serviceAreaId") Long serviceAreaId,
                        @Param("matchStatus") String matchStatus);

    /**
     * 把某服务区域下的地址标记为 STALE。
     *
     * <p>只标记 {@code MATCHED} 的行：本来就没匹配上的地址不需要"重新判断"，
     * 标成 STALE 反而会让运营误以为它曾经可用。</p>
     *
     * @param serviceAreaId 服务区域 ID
     * @return 影响行数
     */
    @Update("UPDATE customer_address SET area_match_status = 'STALE' "
            + "WHERE service_area_id = #{serviceAreaId} AND status = 'ACTIVE' "
            + "AND area_match_status = 'MATCHED'")
    int markStaleByServiceAreaId(@Param("serviceAreaId") Long serviceAreaId);

    /**
     * 记录地址最近使用时间。
     *
     * @param addressId 地址 ID
     * @return 影响行数
     */
    @Update("UPDATE customer_address SET last_used_at = CURRENT_TIMESTAMP(3) WHERE id = #{addressId}")
    int touchLastUsed(@Param("addressId") Long addressId);
}
