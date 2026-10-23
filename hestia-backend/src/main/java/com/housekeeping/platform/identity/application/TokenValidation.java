package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.identity.domain.TokenState;
import com.housekeeping.platform.identity.persistence.entity.AccountEntity;

/**
 * 令牌校验结果。
 *
 * <p>把"结论 + 账号实体"一起返回，避免过滤器为了拿 {@code accountNo} 再查一次库：
 * 每个请求都多一次查询，在 QPS 上来之后就是白送的数据库压力。</p>
 *
 * @param state   校验结论
 * @param account 账号实体，账号不存在时为 {@code null}
 */
public record TokenValidation(TokenState state, AccountEntity account) {

    /**
     * 是否通过。
     *
     * @return 通过返回 {@code true}
     */
    public boolean isValid() {
        return state == TokenState.OK && account != null;
    }
}
