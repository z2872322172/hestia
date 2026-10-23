package com.housekeeping.platform.customer.application.contract;

/**
 * 跨模块使用的客户引用（docs/07 第 11.2 节）。
 *
 * <p>刻意不暴露 {@code CustomerProfileEntity}：其他模块只需要"这个账号对应哪个客户"，
 * 不需要也不应该看到客户表的版本号、审计字段。实体一旦跨模块传递，
 * 客户表加个字段就可能让下单模块编译不过。</p>
 *
 * @param customerId 客户档案 ID，作为订单等业务表的外键
 * @param customerNo 客户编号，对用户可见的业务编号
 * @param status     客户状态
 */
public record CustomerRef(long customerId, String customerNo, String status) {
}
