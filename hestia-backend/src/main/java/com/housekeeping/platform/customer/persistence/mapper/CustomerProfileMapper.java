package com.housekeeping.platform.customer.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.customer.persistence.entity.CustomerProfileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客户档案 Mapper。
 */
@Mapper
public interface CustomerProfileMapper extends BaseMapper<CustomerProfileEntity> {

    /**
     * 按账号查询客户档案。
     *
     * @param accountId 账号 ID
     * @return 客户档案；不存在返回 {@code null}
     */
    @Select("SELECT * FROM customer_profile WHERE account_id = #{accountId} LIMIT 1")
    CustomerProfileEntity selectByAccountId(@Param("accountId") Long accountId);

    /**
     * 按客户编号查询。
     *
     * @param customerNo 客户编号
     * @return 客户档案；不存在返回 {@code null}
     */
    @Select("SELECT * FROM customer_profile WHERE customer_no = #{customerNo} LIMIT 1")
    CustomerProfileEntity selectByCustomerNo(@Param("customerNo") String customerNo);

    /**
     * 对客户档案行加排他锁。
     *
     * <p>用于"地址数量上限"这类<b>跨行约束</b>：上限约束的对象是"该客户的所有地址"，
     * 单条 INSERT 的锁保护不了它，必须先把客户行锁住，让并发新增串行化。
     * 该方法必须在事务中调用，否则锁在语句结束时就释放，等于没锁。</p>
     *
     * @param customerId 客户档案 ID
     * @return 客户 ID；不存在返回 {@code null}
     */
    @Select("SELECT id FROM customer_profile WHERE id = #{customerId} FOR UPDATE")
    Long lockById(@Param("customerId") Long customerId);
}
