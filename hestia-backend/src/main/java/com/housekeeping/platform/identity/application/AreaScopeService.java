package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.application.contract.AreaScope;
import com.housekeeping.platform.identity.application.contract.OperatorAreaScopeService;
import com.housekeeping.platform.identity.domain.RoleCode;
import com.housekeeping.platform.identity.persistence.mapper.AccountAreaScopeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 运营数据范围解析（docs/modules/13 第 6 节）。
 *
 * <p>ADMIN 全量；OPERATOR 按 {@code account_area_scope} 的配置；
 * 其他角色一律空范围。区域为空时返回"空限定范围"而不是抛错——
 * "看不到数据"与"调用失败"是两回事，由调用方决定如何提示。</p>
 */
@Service
@RequiresPersistence
public class AreaScopeService implements OperatorAreaScopeService {

    private final AccountAreaScopeMapper areaScopeMapper;

    public AreaScopeService(AccountAreaScopeMapper areaScopeMapper) {
        this.areaScopeMapper = areaScopeMapper;
    }

    @Override
    public AreaScope resolveAreaScope(long accountId, String roleCode) {
        RoleCode role = RoleCode.of(roleCode).orElse(null);
        if (role == RoleCode.ADMIN) {
            return AreaScope.all();
        }
        if (role != RoleCode.OPERATOR) {
            return AreaScope.ofAreas(List.of());
        }
        List<Long> areaIds = areaScopeMapper.selectServiceAreaIds(accountId, RoleCode.OPERATOR.name());
        return AreaScope.ofAreas(areaIds);
    }
}
