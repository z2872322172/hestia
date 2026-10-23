package com.housekeeping.platform.identity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.identity.persistence.entity.AccountAreaScopeEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账号区域数据范围 Mapper。
 */
@Mapper
public interface AccountAreaScopeMapper extends BaseMapper<AccountAreaScopeEntity> {

    /**
     * 查询账号在某角色下的服务区域 ID。
     *
     * @param accountId 账号 ID
     * @param roleCode  角色编码
     * @return 服务区域 ID 列表
     */
    @Select("SELECT service_area_id FROM account_area_scope "
            + "WHERE account_id = #{accountId} AND role_code = #{roleCode} ORDER BY service_area_id")
    List<Long> selectServiceAreaIds(@Param("accountId") Long accountId, @Param("roleCode") String roleCode);

    /**
     * 查询账号的全部区域范围（后台详情用）。
     *
     * @param accountId 账号 ID
     * @return 区域范围列表
     */
    @Select("SELECT * FROM account_area_scope WHERE account_id = #{accountId} "
            + "ORDER BY role_code, service_area_id")
    List<AccountAreaScopeEntity> selectAllByAccountId(@Param("accountId") Long accountId);

    /**
     * 清空账号在某角色下的区域范围（整体覆盖式更新，先删后插）。
     *
     * @param accountId 账号 ID
     * @param roleCode  角色编码
     * @return 影响行数
     */
    @Delete("DELETE FROM account_area_scope WHERE account_id = #{accountId} AND role_code = #{roleCode}")
    int deleteByAccountAndRole(@Param("accountId") Long accountId, @Param("roleCode") String roleCode);
}
