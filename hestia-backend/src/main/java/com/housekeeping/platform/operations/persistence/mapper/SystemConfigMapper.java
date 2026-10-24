package com.housekeeping.platform.operations.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.operations.persistence.entity.SystemConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 非敏感系统配置 Mapper（docs/modules/13 第 10 节）。
 *
 * <p>修改走"expectedVersion 条件更新"：影响行数 0 说明版本已被他人推进，
 * 调用方返回 SYSTEM_CONFIG_VERSION_CONFLICT；每次成功修改同事务写审计。</p>
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfigEntity> {

    /**
     * 按配置键查询。
     *
     * @param configKey 配置键
     * @return 配置或 null
     */
    @Select("SELECT * FROM system_config WHERE config_key = #{configKey}")
    SystemConfigEntity selectByKey(@Param("configKey") String configKey);

    /**
     * 条件更新配置值（乐观锁）。
     *
     * @param configKey       配置键
     * @param valueJson       新配置值（JSON 编码）
     * @param accountId       修改账号 ID
     * @param expectedVersion 客户端声明的当前版本
     * @return 影响行数，0 = 版本冲突
     */
    @Update("UPDATE system_config SET value_json = #{valueJson}, "
            + "updated_by_account_id = #{accountId}, version = version + 1 "
            + "WHERE config_key = #{configKey} AND version = #{expectedVersion}")
    int updateValue(@Param("configKey") String configKey,
                    @Param("valueJson") String valueJson,
                    @Param("accountId") long accountId,
                    @Param("expectedVersion") long expectedVersion);
}
