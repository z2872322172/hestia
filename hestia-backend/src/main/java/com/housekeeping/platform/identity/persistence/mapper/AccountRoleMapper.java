package com.housekeeping.platform.identity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.identity.persistence.entity.AccountRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账号角色 Mapper。
 */
@Mapper
public interface AccountRoleMapper extends BaseMapper<AccountRoleEntity> {

    /**
     * 查询账号当前有效（ACTIVE）的角色编码。
     *
     * <p>只返回 ACTIVE：SUSPENDED（暂停接单）与 REVOKED（已撤销）都不应出现在
     * 登录响应、角色切换候选和权限判断里。</p>
     *
     * @param accountId 账号 ID
     * @return 角色编码列表，按授予时间升序
     */
    @Select("SELECT role_code FROM account_role WHERE account_id = #{accountId} AND status = 'ACTIVE' "
            + "ORDER BY granted_at ASC, id ASC")
    List<String> selectActiveRoleCodes(@Param("accountId") Long accountId);

    /**
     * 查询账号的全部角色行（含暂停与撤销），用于后台展示。
     *
     * @param accountId 账号 ID
     * @return 角色行
     */
    @Select("SELECT * FROM account_role WHERE account_id = #{accountId} ORDER BY granted_at ASC, id ASC")
    List<AccountRoleEntity> selectAllByAccountId(@Param("accountId") Long accountId);

    /**
     * 查询账号的单个角色行。
     *
     * @param accountId 账号 ID
     * @param roleCode  角色编码
     * @return 角色行；不存在返回 {@code null}
     */
    @Select("SELECT * FROM account_role WHERE account_id = #{accountId} AND role_code = #{roleCode} LIMIT 1")
    AccountRoleEntity selectByAccountAndRole(@Param("accountId") Long accountId,
                                             @Param("roleCode") String roleCode);
}
