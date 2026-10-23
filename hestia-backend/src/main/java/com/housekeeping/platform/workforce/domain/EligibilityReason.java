package com.housekeeping.platform.workforce.domain;

/**
 * 不可派单的原因（docs/modules/05 第 9 节）。
 *
 * <p>资格校验必须给结构化原因而不是一句"不可派单"：运营要按原因分派处理动作
 * （催审核 / 催换证 / 补区域 / 恢复状态），前端也要按原因决定引导页。
 * 只返回 {@code false} 会让运营只能逐个点开人员档案猜。</p>
 *
 * <p>枚举顺序即输出顺序，保证同一组事实永远得到同一份原因列表，便于测试断言。</p>
 */
public enum EligibilityReason {

    /** 账号不是 ACTIVE（停用、待激活、锁定）。 */
    ACCOUNT_NOT_ACTIVE("账号未激活或已停用"),

    /** WORKER 角色被暂停或撤销。 */
    WORKER_ROLE_NOT_ACTIVE("服务人员角色未启用"),

    /** 人员工作状态不是 ACTIVE。 */
    WORKER_NOT_ACTIVE("人员当前不在岗"),

    /** 人员档案未审核通过。 */
    WORKER_AUDIT_NOT_APPROVED("人员档案未审核通过"),

    /** 没有任何已审核技能覆盖该服务项目。 */
    SKILL_NOT_VERIFIED("缺少该服务项目的已审核技能"),

    /** 服务项目要求的某类证书完全没有。 */
    CERTIFICATE_REQUIRED("缺少必需证书"),

    /** 有该类型证书但不可用（过期、撤销、驳回、待审核）。 */
    CERTIFICATE_EXPIRED("证书已过期或不可用"),

    /** 人员没有配置任何有效服务区域。 */
    SERVICE_AREA_NOT_CONFIGURED("未配置服务区域"),

    /** 人员的服务区域不包含订单所在区域。 */
    SERVICE_AREA_NOT_COVERED("服务区域不覆盖该地址");

    private final String description;

    EligibilityReason(String description) {
        this.description = description;
    }

    /**
     * 面向运营的中文说明。
     *
     * @return 说明文本
     */
    public String description() {
        return description;
    }
}
