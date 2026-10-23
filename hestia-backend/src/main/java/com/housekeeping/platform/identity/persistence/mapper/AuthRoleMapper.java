package com.housekeeping.platform.identity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.identity.persistence.entity.AuthRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 角色字典 Mapper。
 *
 * <p>角色字典只读不写：新增角色必须走 Flyway 迁移，避免"某环境手工插了一条角色、
 * 代码里却没有对应枚举"这类不可复现的问题。</p>
 */
@Mapper
public interface AuthRoleMapper extends BaseMapper<AuthRoleEntity> {

    /**
     * 查询角色并校验其处于 ACTIVE。
     *
     * @param roleCode 角色编码
     * @return 角色；不存在或已停用返回 {@code null}
     */
    @Select("SELECT * FROM auth_role WHERE role_code = #{roleCode} AND status = 'ACTIVE' LIMIT 1")
    AuthRoleEntity selectActiveByCode(@Param("roleCode") String roleCode);
}
