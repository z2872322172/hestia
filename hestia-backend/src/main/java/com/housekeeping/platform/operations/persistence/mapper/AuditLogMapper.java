package com.housekeeping.platform.operations.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.operations.persistence.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper（docs/modules/13 第 9 节）。
 *
 * <p><b>只追加</b>：只保留 BaseMapper 的 insert 与查询能力，刻意不提供
 * update / delete 相关封装；审计内容不保存密码、Token 和支付凭证。</p>
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
