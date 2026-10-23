package com.housekeeping.platform.common.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.housekeeping.platform.common.security.CurrentAccountProvider;
import org.apache.ibatis.reflection.MetaObject;

/**
 * 审计字段自动填充。
 *
 * <p>只填"人"，不填时间：{@code created_at} / {@code updated_at} 由数据库
 * {@code DEFAULT CURRENT_TIMESTAMP(3)} 与 {@code ON UPDATE} 维护，避免应用服务器与
 * 数据库时钟不一致导致同一行的时间字段互相矛盾（docs/modules/README.md 第 5.6 节）。</p>
 *
 * <p>系统任务没有登录账号时保持为空，禁止填 0 或 -1 这类"魔法账号"，
 * 否则审计时无法区分"系统操作"和"某个真实账号"。</p>
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATED_BY = "createdByAccountId";

    private static final String UPDATED_BY = "updatedByAccountId";

    private final CurrentAccountProvider currentAccountProvider;

    public AuditMetaObjectHandler(CurrentAccountProvider currentAccountProvider) {
        this.currentAccountProvider = currentAccountProvider;
    }

    /**
     * 插入时填充创建人与最后修改人。
     *
     * @param metaObject MyBatis 实体元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Long accountId = currentAccountProvider.currentAccountId();
        if (accountId == null) {
            return;
        }
        strictInsertFill(metaObject, CREATED_BY, Long.class, accountId);
        strictInsertFill(metaObject, UPDATED_BY, Long.class, accountId);
    }

    /**
     * 更新时填充最后修改人；创建人不允许在更新阶段被覆盖。
     *
     * @param metaObject MyBatis 实体元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        Long accountId = currentAccountProvider.currentAccountId();
        if (accountId == null) {
            return;
        }
        strictUpdateFill(metaObject, UPDATED_BY, Long.class, accountId);
    }
}
