package com.housekeeping.platform.customer.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.application.contract.CustomerQueryService;
import com.housekeeping.platform.customer.application.contract.CustomerRef;
import com.housekeeping.platform.customer.application.view.CustomerProfileView;
import com.housekeeping.platform.customer.domain.CustomerStatus;
import com.housekeeping.platform.customer.persistence.entity.CustomerProfileEntity;
import com.housekeeping.platform.customer.persistence.mapper.CustomerProfileMapper;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户档案服务（跨模块公开契约 {@code CustomerQueryService} 的落地实现）。
 *
 * <p>注册流程：身份模块创建账号 → 调用 {@link #createForAccount(long)} 建档案，
 * 两步必须在<b>同一个事务</b>里，不允许出现"有账号没档案"的半成品数据。</p>
 */
@Service
@RequiresPersistence
public class CustomerProfileService implements CustomerQueryService {

    private static final String CUSTOMER_NO_SEQ_KEY = "CUSTOMER";

    private static final String CUSTOMER_NO_PREFIX = "CUS";

    private final CustomerProfileMapper profileMapper;

    private final BusinessNoGenerator businessNoGenerator;

    public CustomerProfileService(CustomerProfileMapper profileMapper, BusinessNoGenerator businessNoGenerator) {
        this.profileMapper = profileMapper;
        this.businessNoGenerator = businessNoGenerator;
    }

    /**
     * 为账号创建客户档案（消费者注册时调用）。
     *
     * @param accountId 账号 ID
     * @return 新建的档案
     */
    @Transactional
    public CustomerProfileEntity createForAccount(long accountId) {
        CustomerProfileEntity existing = profileMapper.selectByAccountId(accountId);
        if (existing != null) {
            // 幂等：重复调用不重复建档，返回已有档案
            return existing;
        }
        CustomerProfileEntity entity = new CustomerProfileEntity();
        entity.setCustomerNo(businessNoGenerator.next(CUSTOMER_NO_SEQ_KEY, CUSTOMER_NO_PREFIX));
        entity.setAccountId(accountId);
        entity.setStatus(CustomerStatus.ACTIVE.name());
        try {
            profileMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            // 并发注册同一账号时唯一索引兜底，回读已存在的档案
            CustomerProfileEntity concurrent = profileMapper.selectByAccountId(accountId);
            if (concurrent == null) {
                throw exception;
            }
            return concurrent;
        }
        return entity;
    }

    /**
     * 按账号取客户档案。
     *
     * @param accountId 账号 ID
     * @return 档案
     * @throws BusinessException 档案不存在
     */
    @Transactional(readOnly = true)
    public CustomerProfileEntity requireByAccountId(long accountId) {
        CustomerProfileEntity entity = profileMapper.selectByAccountId(accountId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_PROFILE_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 按账号取可用档案（已关闭的档案直接拒绝）。
     *
     * @param accountId 账号 ID
     * @return 档案
     * @throws BusinessException 档案不存在或已关闭
     */
    @Transactional(readOnly = true)
    public CustomerProfileEntity requireUsableByAccountId(long accountId) {
        CustomerProfileEntity entity = requireByAccountId(accountId);
        if (!CustomerStatus.ACTIVE.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CUSTOMER_PROFILE_CLOSED);
        }
        return entity;
    }

    /**
     * 按账号取可用档案，返回跨模块引用（{@code CustomerQueryService} 契约实现）。
     *
     * <p>内部用的 {@link #requireUsableByAccountId(long)} 返回实体，只在客户模块内流转；
     * 跨模块调用走这个方法，只拿到 ID / 编号 / 状态三个字段。</p>
     *
     * @param accountId 账号 ID
     * @return 客户引用
     */
    @Override
    @Transactional(readOnly = true)
    public CustomerRef requireUsable(long accountId) {
        CustomerProfileEntity entity = requireUsableByAccountId(accountId);
        return new CustomerRef(entity.getId(), entity.getCustomerNo(), entity.getStatus());
    }

    /**
     * 客户档案视图。
     *
     * @param accountId 账号 ID
     * @return 视图
     */
    @Transactional(readOnly = true)
    public CustomerProfileView viewOf(long accountId) {
        CustomerProfileEntity entity = requireByAccountId(accountId);
        return new CustomerProfileView(entity.getCustomerNo(), entity.getStatus(), entity.getCreatedAt());
    }

    /**
     * 关闭客户档案。
     *
     * <p>只关业务身份，不动账号：同一个人可能还是服务人员。</p>
     *
     * @param accountId 账号 ID
     */
    @Transactional
    public void close(long accountId) {
        CustomerProfileEntity entity = requireByAccountId(accountId);
        if (CustomerStatus.CLOSED.name().equals(entity.getStatus())) {
            return;
        }
        entity.setStatus(CustomerStatus.CLOSED.name());
        profileMapper.updateById(entity);
    }
}
