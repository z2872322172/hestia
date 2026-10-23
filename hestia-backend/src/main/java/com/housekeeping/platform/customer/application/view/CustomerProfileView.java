package com.housekeeping.platform.customer.application.view;

import java.time.LocalDateTime;

/**
 * 客户档案视图。
 *
 * <p>只含业务编号与状态，不含手机号与显示名称：那些属于账号域，
 * 需要时通过身份模块的 {@code AccountQueryService} 取，不在客户表里复制一份。</p>
 *
 * @param customerNo 客户编号
 * @param status     状态
 * @param createdAt  建档时间
 */
public record CustomerProfileView(String customerNo, String status, LocalDateTime createdAt) {
}
