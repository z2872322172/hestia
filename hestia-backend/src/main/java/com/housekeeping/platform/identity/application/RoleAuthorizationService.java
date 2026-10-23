package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.domain.RoleCode;
import com.housekeeping.platform.identity.domain.RoleCombinationPolicy;
import com.housekeeping.platform.identity.persistence.entity.AccountRoleEntity;
import com.housekeeping.platform.identity.persistence.mapper.AccountRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 角色授权服务（跨模块公开契约 {@code RoleAuthorizationService}）。
 *
 * <p>只回答"这个账号有哪些角色、这个角色现在能不能用"，
 * <b>不</b>回答"这个账号能不能看某条订单"——那是业务模块结合数据范围的职责
 * （docs/modules/01 第 8.4 节）。</p>
 */
@Service
@RequiresPersistence
public class RoleAuthorizationService {

    private final AccountRoleMapper accountRoleMapper;

    public RoleAuthorizationService(AccountRoleMapper accountRoleMapper) {
        this.accountRoleMapper = accountRoleMapper;
    }

    /**
     * 查询有效角色。
     *
     * @param accountId 账号 ID
     * @return 角色编码列表，按授予顺序
     */
    @Transactional(readOnly = true)
    public List<String> activeRoleCodes(long accountId) {
        return accountRoleMapper.selectActiveRoleCodes(accountId);
    }

    /**
     * 校验账号拥有可用角色。
     *
     * <p>区分"没授予"和"被暂停"：前者是业务上没开通，后者是运营主动停用，
     * 用户看到的提示与后续处理方式完全不同。</p>
     *
     * @param accountId 账号 ID
     * @param roleCode  目标角色
     * @throws BusinessException 未授予或被暂停
     */
    @Transactional(readOnly = true)
    public void requireActiveRole(long accountId, RoleCode roleCode) {
        AccountRoleEntity role = accountRoleMapper.selectByAccountAndRole(accountId, roleCode.name());
        if (role == null) {
            throw new BusinessException(ErrorCode.AUTH_ROLE_NOT_GRANTED);
        }
        if (!"ACTIVE".equals(role.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_ROLE_SUSPENDED);
        }
    }

    /**
     * 选择活动角色。
     *
     * <p>默认取最早授予的角色：消费者注册时先拿到 CUSTOMER，
     * 之后被邀请为服务人员才追加 WORKER，因此默认身份仍是消费者——
     * 这符合"同一个人先是客户，后来才成为服务人员"的现实顺序。</p>
     *
     * @param roleCodes 有效角色编码
     * @return 活动角色；没有角色返回 {@code null}
     */
    public String pickActiveRole(List<String> roleCodes) {
        return roleCodes.isEmpty() ? null : roleCodes.get(0);
    }

    /**
     * 校验角色组合合法性。
     *
     * @param roles 目标角色
     * @throws BusinessException 内部角色与外部角色混用
     */
    public void assertCombinable(Collection<RoleCode> roles) {
        try {
            RoleCombinationPolicy.requireAllowed(roles);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        }
    }

    /**
     * 解析角色编码。
     *
     * @param roleCodes 角色编码
     * @return 角色枚举
     * @throws BusinessException 存在非法角色编码
     */
    public List<RoleCode> parseRoles(Collection<String> roleCodes) {
        return roleCodes.stream()
                .map(code -> RoleCode.of(code)
                        .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                                "角色编码非法：" + code)))
                .toList();
    }
}
