package com.housekeeping.platform.identity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.identity.persistence.entity.AccountLoginLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper。
 *
 * <p>只使用 {@code insert} 与查询：日志表没有 update/delete 的调用方，
 * 一旦业务代码里出现"改登录日志"，就意味着取证链被破坏。</p>
 */
@Mapper
public interface AccountLoginLogMapper extends BaseMapper<AccountLoginLogEntity> {
}
