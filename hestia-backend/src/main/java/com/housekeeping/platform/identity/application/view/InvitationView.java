package com.housekeeping.platform.identity.application.view;

import java.time.LocalDateTime;

/**
 * 服务人员邀请结果。
 *
 * <p>{@code activationToken} <b>只在这里返回一次</b>：数据库只存哈希，
 * 后续任何接口都无法再取回明文凭证。实际投递靠短信/邮件（批次 5 的通知模块），
 * 首版由运营手工转达，因此必须把凭证返回给调用方。</p>
 *
 * @param activationToken  一次性激活凭证，账号已激活时为 {@code null}
 * @param expiresAt        凭证过期时间
 * @param accountNo        账号编号
 * @param activationRequired 是否需要激活
 * @param message          补充说明
 */
public record InvitationView(String activationToken, LocalDateTime expiresAt, String accountNo,
                             boolean activationRequired, String message) {
}
