package com.housekeeping.platform.workforce.application.view;

import java.time.LocalDateTime;

/**
 * 创建人员档案的结果。
 *
 * <p>{@code activationToken} 与身份模块的邀请凭证同源：只在创建时返回一次，
 * 库里只有哈希。运营必须当场转达，之后无法再取回——这是"一次性凭证"的定义。</p>
 *
 * @param workerNo            人员编号
 * @param accountNo           绑定账号编号
 * @param activationToken     一次性激活凭证，账号已激活时为 {@code null}
 * @param activationExpiresAt 凭证过期时间
 * @param activationRequired  是否需要激活
 * @param message             补充说明
 */
public record CreatedWorkerView(String workerNo,
                                String accountNo,
                                String activationToken,
                                LocalDateTime activationExpiresAt,
                                boolean activationRequired,
                                String message) {
}
