package com.housekeeping.platform.common.api;

import org.springframework.http.HttpStatus;

/**
 * 稳定错误码表。
 *
 * <p>错误码是前后端契约的一部分：一旦发布不得改名字面量，只能新增。
 * HTTP 状态码用于网关和监控告警，业务错误码用于前端分支处理，两者配合使用
 * （docs/03 第 9.3 节、docs/07 第 13.4 节）。</p>
 */
public enum ErrorCode {

    /** 未携带有效凭证访问受保护资源。 */
    AUTH_REQUIRED("AUTH_REQUIRED", HttpStatus.UNAUTHORIZED, "请先登录"),

    /**
     * 凭证格式错误、签名不合法或已过期。
     *
     * <p>docs/modules/01 第 15 节把该场景拆成 {@code AUTH_TOKEN_INVALID} 与
     * {@code AUTH_TOKEN_EXPIRED} 两个码；本实现保留 {@code AUTH_INVALID_TOKEN}
     * 作为"无效"的唯一字面量，过期单独用 {@link #AUTH_TOKEN_EXPIRED}，
     * 不新增含义重复的第三个码。</p>
     */
    AUTH_INVALID_TOKEN("AUTH_INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "登录凭证无效或已过期"),

    /** 账号或密码错误；对外不区分"账号不存在"与"密码错误"，避免账号枚举。 */
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "账号或密码错误"),

    /** 已认证但角色或数据范围不足。 */
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", HttpStatus.FORBIDDEN, "没有权限访问该资源"),

    /** 令牌已过期，与"无效"区分开，前端可据此静默刷新或跳登录。 */
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录"),

    /** 账号被管理员停用。 */
    AUTH_ACCOUNT_DISABLED("AUTH_ACCOUNT_DISABLED", HttpStatus.FORBIDDEN, "账号已停用，请联系管理员"),

    /** 连续登录失败导致临时锁定。 */
    AUTH_ACCOUNT_LOCKED("AUTH_ACCOUNT_LOCKED", HttpStatus.FORBIDDEN, "账号已临时锁定，请稍后再试"),

    /** 邀请已发出但尚未设置密码。 */
    AUTH_PENDING_ACTIVATION("AUTH_PENDING_ACTIVATION", HttpStatus.FORBIDDEN, "账号尚未激活，请先完成激活"),

    /** 账号未分配目标角色。 */
    AUTH_ROLE_NOT_GRANTED("AUTH_ROLE_NOT_GRANTED", HttpStatus.FORBIDDEN, "当前账号未开通该身份"),

    /** 目标角色被暂停或撤销。 */
    AUTH_ROLE_SUSPENDED("AUTH_ROLE_SUSPENDED", HttpStatus.FORBIDDEN, "该身份已被暂停，请联系管理员"),

    /** 密码不符合长度或复杂度要求。 */
    AUTH_PASSWORD_POLICY_VIOLATION("AUTH_PASSWORD_POLICY_VIOLATION", HttpStatus.BAD_REQUEST, "密码不符合安全要求"),

    /** 邀请或重置凭证不存在、已被使用。 */
    AUTH_INVITATION_INVALID("AUTH_INVITATION_INVALID", HttpStatus.BAD_REQUEST, "激活链接无效或已被使用"),

    /** 邀请或重置凭证已过期。 */
    AUTH_INVITATION_EXPIRED("AUTH_INVITATION_EXPIRED", HttpStatus.BAD_REQUEST, "激活链接已过期，请重新获取"),

    /** 手机号已被注册。 */
    AUTH_PHONE_ALREADY_EXISTS("AUTH_PHONE_ALREADY_EXISTS", HttpStatus.CONFLICT, "该手机号已注册"),

    /** 客户档案不存在或已关闭。 */
    CUSTOMER_PROFILE_NOT_FOUND("CUSTOMER_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "客户档案不存在"),

    /** 客户档案已关闭，不能继续下单或维护地址。 */
    CUSTOMER_PROFILE_CLOSED("CUSTOMER_PROFILE_CLOSED", HttpStatus.CONFLICT, "客户档案已关闭"),

    /**
     * 地址不存在。
     *
     * <p>越权访问他人地址也返回本码：通过"不存在"与"无权限"的差异可以探测
     * 地址编号是否存在，因此两者必须使用同一个响应。</p>
     */
    ADDRESS_NOT_FOUND("ADDRESS_NOT_FOUND", HttpStatus.NOT_FOUND, "地址不存在"),

    /** 地址已被删除。 */
    ADDRESS_DELETED("ADDRESS_DELETED", HttpStatus.CONFLICT, "地址已删除"),

    /** 地址数量超过上限。 */
    ADDRESS_LIMIT_EXCEEDED("ADDRESS_LIMIT_EXCEEDED", HttpStatus.CONFLICT, "地址数量已达上限"),

    /** 并发设置默认地址，后到的请求被唯一索引拒绝。 */
    ADDRESS_DEFAULT_CONFLICT("ADDRESS_DEFAULT_CONFLICT", HttpStatus.CONFLICT, "默认地址已被其他操作修改，请刷新后重试"),

    /** 地址版本号不一致（乐观锁）。 */
    ADDRESS_VERSION_CONFLICT("ADDRESS_VERSION_CONFLICT", HttpStatus.CONFLICT, "地址已被修改，请刷新后重试"),

    /** 地址尚未匹配到服务区域。 */
    ADDRESS_AREA_NOT_MATCHED("ADDRESS_AREA_NOT_MATCHED", HttpStatus.UNPROCESSABLE_ENTITY, "地址尚未确认服务范围"),

    /** 地址不在服务范围内。 */
    ADDRESS_AREA_OUT_OF_RANGE("ADDRESS_AREA_OUT_OF_RANGE", HttpStatus.UNPROCESSABLE_ENTITY, "当前地址暂不在服务范围内"),

    /** 行政区编码不存在或层级错误。 */
    REGION_NOT_FOUND("REGION_NOT_FOUND", HttpStatus.BAD_REQUEST, "行政区信息有误"),

    /** 行政区已停用，不能再被新地址选择。 */
    REGION_DISABLED("REGION_DISABLED", HttpStatus.CONFLICT, "该行政区已停用"),

    /** 行政区层级不符合要求。 */
    REGION_LEVEL_MISMATCH("REGION_LEVEL_MISMATCH", HttpStatus.BAD_REQUEST, "行政区层级不匹配"),

    /** 行政区上级关系错误。 */
    REGION_PARENT_INVALID("REGION_PARENT_INVALID", HttpStatus.BAD_REQUEST, "行政区上级关系错误"),

    /** 服务区域不存在。 */
    SERVICE_AREA_NOT_FOUND("SERVICE_AREA_NOT_FOUND", HttpStatus.NOT_FOUND, "服务区域不存在"),

    /** 服务区域当前状态不允许该操作。 */
    SERVICE_AREA_NOT_ACTIVE("SERVICE_AREA_NOT_ACTIVE", HttpStatus.CONFLICT, "服务区域当前状态不允许该操作"),

    /** 服务区域生效时间区间非法。 */
    SERVICE_AREA_EFFECTIVE_RANGE_INVALID("SERVICE_AREA_EFFECTIVE_RANGE_INVALID", HttpStatus.BAD_REQUEST, "生效时间区间不合法"),

    /** 启用服务区域前必须至少配置一条有效覆盖。 */
    SERVICE_AREA_COVERAGE_REQUIRED("SERVICE_AREA_COVERAGE_REQUIRED", HttpStatus.CONFLICT, "请先配置至少一条覆盖范围"),

    /** 覆盖的行政区不属于服务区域所在城市。 */
    SERVICE_AREA_COVERAGE_OUTSIDE_CITY("SERVICE_AREA_COVERAGE_OUTSIDE_CITY", HttpStatus.BAD_REQUEST, "覆盖范围必须属于服务区域所在城市"),

    /** 覆盖级别与行政区级别不一致。 */
    SERVICE_AREA_COVERAGE_LEVEL_MISMATCH("SERVICE_AREA_COVERAGE_LEVEL_MISMATCH", HttpStatus.BAD_REQUEST, "覆盖级别与行政区级别不一致"),

    /** 同一行政区已被该服务区域覆盖。 */
    SERVICE_AREA_COVERAGE_CONFLICT("SERVICE_AREA_COVERAGE_CONFLICT", HttpStatus.CONFLICT, "该覆盖范围已存在"),

    /** 同级同优先级出现多个候选服务区域，需运营人员处理。 */
    SERVICE_AREA_AMBIGUOUS("SERVICE_AREA_AMBIGUOUS", HttpStatus.CONFLICT, "当前地址匹配到多个同优先级服务区域，请联系客服"),

    /** 服务区域版本号不一致（乐观锁）。 */
    SERVICE_AREA_VERSION_CONFLICT("SERVICE_AREA_VERSION_CONFLICT", HttpStatus.CONFLICT, "服务区域已被修改，请刷新后重试"),

    /** 服务区域已归档。 */
    SERVICE_AREA_ARCHIVED("SERVICE_AREA_ARCHIVED", HttpStatus.CONFLICT, "服务区域已归档"),

    /** 服务分类不存在。 */
    SERVICE_CATEGORY_NOT_FOUND("SERVICE_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "服务分类不存在"),

    /** 服务分类已停用，不能再挂载服务项目，也不在前台展示。 */
    SERVICE_CATEGORY_DISABLED("SERVICE_CATEGORY_DISABLED", HttpStatus.CONFLICT, "服务分类已停用"),

    /**
     * 分类层级超过限制。
     *
     * <p>docs/modules/04 第 5.1 节：数据库支持多级树，企业首版限制最多三层。
     * 该限制写在业务层而不是数据库，因为它是产品约束不是数据完整性约束。</p>
     */
    SERVICE_CATEGORY_DEPTH_EXCEEDED("SERVICE_CATEGORY_DEPTH_EXCEEDED", HttpStatus.BAD_REQUEST,
            "服务分类最多支持三层"),

    /**
     * 分类下还有未下架的服务项目，不能停用。
     *
     * <p>停用分类会让这些项目从前台凭空消失（前台按父链状态过滤），
     * 运营看到的现象是"商品不见了"而不是"分类停用了"，极难排查。
     * 要求先把项目下架，动作与后果才一一对应。</p>
     */
    SERVICE_CATEGORY_HAS_ACTIVE_ITEMS("SERVICE_CATEGORY_HAS_ACTIVE_ITEMS", HttpStatus.CONFLICT,
            "分类下还有未下架的服务项目，请先下架"),

    /** 服务项目不存在。 */
    SERVICE_ITEM_NOT_FOUND("SERVICE_ITEM_NOT_FOUND", HttpStatus.NOT_FOUND, "服务项目不存在"),

    /**
     * 服务项目尚未发布，不可下单。
     *
     * <p><b>当前没有调用点，是批次 3（下单）预留。</b>公开查询与报价刻意不抛它——
     * 草稿项目对消费者必须表现得和"不存在"完全一致（否则靠错误码就能探测未上架商品）。
     * 真正需要区分"不存在"和"存在但买不了"的地方是下单：客户购物车里的项目在提交时
     * 可能刚被下架，这时回 404 会让客户以为链接失效，回本码才能给出可操作的提示。</p>
     */
    SERVICE_ITEM_NOT_PUBLISHED("SERVICE_ITEM_NOT_PUBLISHED", HttpStatus.CONFLICT, "服务项目未发布，暂不可下单"),

    /**
     * 服务项目已下架，不可创建新订单。
     *
     * <p>同 {@link #SERVICE_ITEM_NOT_PUBLISHED}：批次 3 下单校验使用，公开查询不抛。</p>
     */
    SERVICE_ITEM_OFFLINE("SERVICE_ITEM_OFFLINE", HttpStatus.CONFLICT, "服务项目已下架"),

    /** 服务项目当前状态不允许执行该操作（如重复发布）。 */
    SERVICE_ITEM_STATUS_CONFLICT("SERVICE_ITEM_STATUS_CONFLICT", HttpStatus.CONFLICT,
            "服务项目当前状态不允许该操作"),

    /** 服务项目已被他人修改，乐观锁校验失败。 */
    SERVICE_ITEM_VERSION_CONFLICT("SERVICE_ITEM_VERSION_CONFLICT", HttpStatus.CONFLICT,
            "服务项目已被他人修改，请刷新后重试"),

    /** 必选规格组没有选择任何值。 */
    SERVICE_OPTION_REQUIRED("SERVICE_OPTION_REQUIRED", HttpStatus.BAD_REQUEST, "请选择必填规格"),

    /** 规格组或规格值不存在、已停用，或不属于当前服务项目。 */
    SERVICE_OPTION_INVALID("SERVICE_OPTION_INVALID", HttpStatus.BAD_REQUEST, "规格选项无效"),

    /** 单选组选了多个值，或多选组选择数量越界。 */
    SERVICE_OPTION_SELECTION_LIMIT("SERVICE_OPTION_SELECTION_LIMIT", HttpStatus.BAD_REQUEST,
            "规格选择数量超出限制"),

    /** 没有任何阶梯能覆盖本次的度量值。 */
    SERVICE_PRICE_TIER_NOT_FOUND("SERVICE_PRICE_TIER_NOT_FOUND", HttpStatus.UNPROCESSABLE_ENTITY,
            "没有匹配的价格阶梯"),

    /** 同一阶梯类型下区间重叠。 */
    SERVICE_PRICE_TIER_CONFLICT("SERVICE_PRICE_TIER_CONFLICT", HttpStatus.CONFLICT, "价格阶梯区间重叠"),

    /**
     * 客户端展示金额与服务端重算结果不一致。
     *
     * <p><b>当前没有调用点，是批次 3（下单）预留。</b>下单时服务端必须用
     * {@code PriceCalculator} 重算一遍，与前端带回来的金额比对：不一致就拒绝，
     * 而不是"以服务端为准静默改价"——用户看到的价格与实际支付的价格必须一致。</p>
     */
    SERVICE_PRICE_CHANGED("SERVICE_PRICE_CHANGED", HttpStatus.CONFLICT, "价格已变化，请重新确认"),

    /** 发布服务项目时缺少服务标准检查项。 */
    SERVICE_STANDARD_REQUIRED("SERVICE_STANDARD_REQUIRED", HttpStatus.CONFLICT, "发布前必须配置服务标准"),

    /** 购买数量超出服务项目允许的上下限。 */
    SERVICE_QUANTITY_OUT_OF_RANGE("SERVICE_QUANTITY_OUT_OF_RANGE", HttpStatus.BAD_REQUEST,
            "购买数量超出允许范围"),

    /** 人员档案不存在。 */
    WORKER_PROFILE_NOT_FOUND("WORKER_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "服务人员档案不存在"),

    /** 人员当前不可接单（未审核、已暂停、已离职）。 */
    WORKER_NOT_ACTIVE("WORKER_NOT_ACTIVE", HttpStatus.CONFLICT, "服务人员当前不可接单"),

    /** 人员档案未审核通过，不能进入在岗状态。 */
    WORKER_AUDIT_NOT_APPROVED("WORKER_AUDIT_NOT_APPROVED", HttpStatus.CONFLICT, "人员档案未审核通过"),

    /** 该账号已经绑定人员档案。 */
    WORKER_ACCOUNT_ALREADY_BOUND("WORKER_ACCOUNT_ALREADY_BOUND", HttpStatus.CONFLICT, "该账号已绑定服务人员档案"),

    /**
     * 人员档案版本冲突（乐观锁）。
     *
     * <p>档案会被运营与本人同时修改，没有版本号就是后写覆盖先写。</p>
     */
    WORKER_VERSION_CONFLICT("WORKER_VERSION_CONFLICT", HttpStatus.CONFLICT,
            "人员档案已被他人修改，请刷新后重试"),

    /**
     * 人员状态不允许该流转。
     *
     * <p>例如对已离职人员调用"恢复"：{@code LEFT} 是终态，
     * 重新入职必须走新档案，不能把旧档案改回 {@code ACTIVE}。</p>
     */
    WORKER_STATUS_CONFLICT("WORKER_STATUS_CONFLICT", HttpStatus.CONFLICT, "人员当前状态不允许该操作"),

    /** 技能对应的服务项目不存在或已下架。 */
    WORKER_SKILL_INVALID("WORKER_SKILL_INVALID", HttpStatus.BAD_REQUEST, "技能对应的服务项目无效"),

    /** 证书类型或证书信息非法。 */
    WORKER_CERTIFICATE_INVALID("WORKER_CERTIFICATE_INVALID", HttpStatus.BAD_REQUEST, "证书信息不合法"),

    /** 同一人同一类型重复登记同一张证书。 */
    WORKER_CERTIFICATE_DUPLICATE("WORKER_CERTIFICATE_DUPLICATE", HttpStatus.CONFLICT, "该证书已登记"),

    /** 证书已被他人修改（乐观锁）。 */
    WORKER_CERTIFICATE_VERSION_CONFLICT("WORKER_CERTIFICATE_VERSION_CONFLICT", HttpStatus.CONFLICT,
            "证书已被他人修改，请刷新后重试"),

    /**
     * 未配置字段加密密钥，无法接收证书编号。
     *
     * <p>宁可拒绝，也不能退化成明文存储：明文落库之后，即使补上密钥，
     * 已经写进去的那部分也只能按泄漏处理。</p>
     */
    WORKER_CERTIFICATE_CIPHER_UNAVAILABLE("WORKER_CERTIFICATE_CIPHER_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE, "服务端未配置字段加密密钥，暂不能接收证书编号"),

    /** 服务区域不存在、已归档或不可分配。 */
    WORKER_SERVICE_AREA_INVALID("WORKER_SERVICE_AREA_INVALID", HttpStatus.BAD_REQUEST,
            "服务区域不可分配给人员"),

    // ---------------------------------------------------------------------------
    // scheduling（docs/modules/06 第 20 节）
    // ---------------------------------------------------------------------------

    /** 标准时间窗不存在。 */
    SERVICE_WINDOW_NOT_FOUND("SERVICE_WINDOW_NOT_FOUND", HttpStatus.NOT_FOUND, "服务时间窗不存在"),

    /** 时间窗已停用，不能参与排班与预约。 */
    SERVICE_WINDOW_DISABLED("SERVICE_WINDOW_DISABLED", HttpStatus.CONFLICT, "服务时间窗已停用"),

    /** 新时间窗与现有时间窗时间区间重叠。 */
    SERVICE_WINDOW_OVERLAP("SERVICE_WINDOW_OVERLAP", HttpStatus.CONFLICT, "时间窗与其他时间窗重叠"),

    /** 时间窗已被并发修改，版本号不匹配。 */
    SERVICE_WINDOW_VERSION_CONFLICT("SERVICE_WINDOW_VERSION_CONFLICT", HttpStatus.CONFLICT,
            "时间窗已被修改，请刷新后重试"),

    /** 服务项目预计时长超过目标时间窗长度。 */
    SERVICE_DURATION_EXCEEDS_WINDOW("SERVICE_DURATION_EXCEEDS_WINDOW", HttpStatus.CONFLICT,
            "服务时长超过时间窗长度"),

    /** 排班记录不存在。 */
    WORKER_SCHEDULE_NOT_FOUND("WORKER_SCHEDULE_NOT_FOUND", HttpStatus.NOT_FOUND, "排班记录不存在"),

    /** 同一人员同一日期同一时间窗已有排班。 */
    WORKER_SCHEDULE_CONFLICT("WORKER_SCHEDULE_CONFLICT", HttpStatus.CONFLICT, "该日期时间窗已有排班"),

    /** 人员时段不存在。 */
    WORKER_SLOT_NOT_FOUND("WORKER_SLOT_NOT_FOUND", HttpStatus.NOT_FOUND, "人员时段不存在"),

    /** 时段当前状态不允许锁定（非 AVAILABLE）。 */
    WORKER_SLOT_NOT_AVAILABLE("WORKER_SLOT_NOT_AVAILABLE", HttpStatus.CONFLICT, "时段已被占用"),

    /** 时段已被其他订单锁定。 */
    WORKER_SLOT_ALREADY_HELD("WORKER_SLOT_ALREADY_HELD", HttpStatus.CONFLICT, "时段已被其他订单锁定"),

    /** 时段已被支付订单正式占用。 */
    WORKER_SLOT_ALREADY_BOOKED("WORKER_SLOT_ALREADY_BOOKED", HttpStatus.CONFLICT, "时段已被已支付订单占用"),

    /** 时段锁定已过期，需要重新下单。 */
    WORKER_SLOT_HOLD_EXPIRED("WORKER_SLOT_HOLD_EXPIRED", HttpStatus.CONFLICT, "时段锁定已过期，请重新下单"),

    /** 释放时段的条件更新未命中，时段状态已被并发操作改变。 */
    WORKER_SLOT_RELEASE_CONFLICT("WORKER_SLOT_RELEASE_CONFLICT", HttpStatus.CONFLICT,
            "时段状态已变化，释放失败"),

    /** 排班存在已占用（HELD/BOOKED）时段，不能直接请假或停排。 */
    SCHEDULE_CHANGE_HAS_BOOKED_ORDER("SCHEDULE_CHANGE_HAS_BOOKED_ORDER", HttpStatus.CONFLICT,
            "该排班存在待支付或已支付订单，请先改派或改期"),

    // ---------------------------------------------------------------------------
    // booking / dispatch（docs/modules/07 第 20 节、docs/modules/08 第 18 节）
    // ---------------------------------------------------------------------------

    /** 订单不存在。 */
    BOOKING_NOT_FOUND("BOOKING_NOT_FOUND", HttpStatus.NOT_FOUND, "订单不存在"),

    /**
     * 订单不属于当前用户。
     *
     * <p>与 {@code ADDRESS_NOT_FOUND} 同理：返回"不存在"而非"无权限"，
     * 避免通过错误码差异探测他人订单号。</p>
     */
    BOOKING_NOT_OWNED("BOOKING_NOT_OWNED", HttpStatus.NOT_FOUND, "订单不存在"),

    /** 地址未匹配到可用服务区域，不能下单。 */
    BOOKING_SERVICE_AREA_NOT_MATCHED("BOOKING_SERVICE_AREA_NOT_MATCHED",
            HttpStatus.UNPROCESSABLE_ENTITY, "当前地址暂不在服务范围内"),

    /** 订单当前状态不允许取消（如服务进行中只能走售后）。 */
    BOOKING_CANNOT_CANCEL("BOOKING_CANNOT_CANCEL", HttpStatus.CONFLICT, "订单当前状态不支持取消"),

    /** 订单当前状态不允许改期（仅已支付且未出发的订单可改期）。 */
    BOOKING_CANNOT_RESCHEDULE("BOOKING_CANNOT_RESCHEDULE", HttpStatus.CONFLICT, "订单当前状态不支持改期"),

    /** 派单任务或分配记录状态不允许该流转。 */
    DISPATCH_JOB_CONFLICT("DISPATCH_JOB_CONFLICT", HttpStatus.CONFLICT, "派单状态不允许该操作"),

    /** 目标资源不存在，或当前账号无权感知其存在。 */
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "资源不存在"),

    /** 请求参数缺失、格式错误或未通过 Jakarta Validation 校验。 */
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "请求参数校验失败"),

    /** 目标时段已被占用或不可预约。 */
    SLOT_NOT_AVAILABLE("SLOT_NOT_AVAILABLE", HttpStatus.CONFLICT, "该时段已不可预约"),

    /** 订单当前状态不允许执行该操作。 */
    BOOKING_STATUS_CONFLICT("BOOKING_STATUS_CONFLICT", HttpStatus.CONFLICT, "订单状态不允许该操作"),

    /** 支付金额与订单应付金额不一致。 */
    PAYMENT_AMOUNT_MISMATCH("PAYMENT_AMOUNT_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY, "支付金额与订单金额不一致"),

    /** 订单已支付成功，不允许重复支付。 */
    PAYMENT_ALREADY_SUCCESS("PAYMENT_ALREADY_SUCCESS", HttpStatus.CONFLICT, "订单已支付成功"),

    // ---------------------------------------------------------------------------
    // payment / refund（docs/modules/10 第 17 节）
    // ---------------------------------------------------------------------------

    /** 支付单不存在。 */
    PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "支付单不存在"),

    /**
     * 支付单不属于当前用户。
     *
     * <p>与 {@code BOOKING_NOT_OWNED} 同理：不区分"不存在"与"无权限"，防探测。</p>
     */
    PAYMENT_NOT_OWNED("PAYMENT_NOT_OWNED", HttpStatus.NOT_FOUND, "支付单不存在"),

    /** 订单当前状态不允许发起支付（非待支付 / 已取消 / 已支付）。 */
    PAYMENT_ORDER_NOT_PAYABLE("PAYMENT_ORDER_NOT_PAYABLE", HttpStatus.CONFLICT, "订单当前状态不允许支付"),

    /** 支付已超过订单支付截止时间。 */
    PAYMENT_TIMEOUT("PAYMENT_TIMEOUT", HttpStatus.CONFLICT, "支付已超时，请重新下单"),

    /** 支付渠道未启用。 */
    PAYMENT_CHANNEL_DISABLED("PAYMENT_CHANNEL_DISABLED", HttpStatus.BAD_REQUEST, "支付渠道暂不可用"),

    /** 回调报文不合法：签名不符、金额不符或支付单不存在。 */
    PAYMENT_CALLBACK_INVALID("PAYMENT_CALLBACK_INVALID", HttpStatus.BAD_REQUEST, "支付回调不合法"),

    /** 同一回调已处理过（幂等命中）。 */
    PAYMENT_CALLBACK_DUPLICATE("PAYMENT_CALLBACK_DUPLICATE", HttpStatus.CONFLICT, "支付回调重复"),

    /** 支付网关调用失败。 */
    PAYMENT_GATEWAY_ERROR("PAYMENT_GATEWAY_ERROR", HttpStatus.BAD_GATEWAY, "支付渠道异常，请稍后重试"),

    /** 退款单不存在。 */
    REFUND_NOT_FOUND("REFUND_NOT_FOUND", HttpStatus.NOT_FOUND, "退款单不存在"),

    /** 当前订单状态不允许发起退款。 */
    REFUND_NOT_ALLOWED("REFUND_NOT_ALLOWED", HttpStatus.CONFLICT, "当前订单不支持退款"),

    /** 退款金额超过剩余可退金额。 */
    REFUND_AMOUNT_EXCEEDED("REFUND_AMOUNT_EXCEEDED", HttpStatus.CONFLICT, "退款金额超过可退金额"),

    /** 退款单当前状态不允许该操作。 */
    REFUND_STATUS_CONFLICT("REFUND_STATUS_CONFLICT", HttpStatus.CONFLICT, "退款单状态不允许该操作"),

    /** 退款必须经运营审批后执行。 */
    REFUND_APPROVAL_REQUIRED("REFUND_APPROVAL_REQUIRED", HttpStatus.CONFLICT, "退款需运营审批后执行"),

    /** 退款网关调用失败。 */
    REFUND_GATEWAY_ERROR("REFUND_GATEWAY_ERROR", HttpStatus.BAD_GATEWAY, "退款渠道异常，请稍后重试"),

    /** 退款重试次数已达上限。 */
    REFUND_RETRY_LIMIT_REACHED("REFUND_RETRY_LIMIT_REACHED", HttpStatus.CONFLICT, "退款重试次数已达上限"),

    // ---------------------------------------------------------------------------
    // dispatch 接单 / 改派（docs/modules/08 第 18 节，任务状态冲突复用 DISPATCH_JOB_CONFLICT）
    // ---------------------------------------------------------------------------

    /** 没有可用候选人员。 */
    DISPATCH_NO_CANDIDATE("DISPATCH_NO_CANDIDATE", HttpStatus.CONFLICT, "暂无可派单的服务人员"),

    /** 分配记录不存在。 */
    DISPATCH_ASSIGNMENT_NOT_FOUND("DISPATCH_ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "派单分配不存在"),

    /**
     * 分配记录不属于当前服务人员。
     *
     * <p>与 {@code BOOKING_NOT_OWNED} 同理：统一回"不存在"，防止探测他人派单号。</p>
     */
    DISPATCH_ASSIGNMENT_NOT_OWNED("DISPATCH_ASSIGNMENT_NOT_OWNED", HttpStatus.NOT_FOUND, "派单分配不存在"),

    /** 接单已超时，分配已失效。 */
    DISPATCH_ASSIGNMENT_EXPIRED("DISPATCH_ASSIGNMENT_EXPIRED", HttpStatus.CONFLICT, "接单已超时失效"),

    /** 拒单必须填写原因。 */
    DISPATCH_REJECT_REASON_REQUIRED("DISPATCH_REJECT_REASON_REQUIRED", HttpStatus.BAD_REQUEST,
            "拒单必须填写原因"),

    /** 派单任务进入人工处理队列。 */
    DISPATCH_MANUAL_REQUIRED("DISPATCH_MANUAL_REQUIRED", HttpStatus.CONFLICT, "派单需人工处理"),

    /** 派单数据被并发修改（自动改派与人工改派竞争）。 */
    DISPATCH_CONCURRENT_CHANGE("DISPATCH_CONCURRENT_CHANGE", HttpStatus.CONFLICT,
            "派单状态已变化，请刷新后重试"),

    // ---------------------------------------------------------------------------
    // fulfillment（docs/modules/09 第 19 节）
    // ---------------------------------------------------------------------------

    /** 履约记录不存在。 */
    FULFILLMENT_NOT_FOUND("FULFILLMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "履约记录不存在"),

    /**
     * 履约记录不属于当前用户。
     *
     * <p>与 {@code BOOKING_NOT_OWNED} 同理：统一回"不存在"防探测。</p>
     */
    FULFILLMENT_NOT_OWNED("FULFILLMENT_NOT_OWNED", HttpStatus.NOT_FOUND, "履约记录不存在"),

    /** 履约当前状态不允许该操作。 */
    FULFILLMENT_STATUS_CONFLICT("FULFILLMENT_STATUS_CONFLICT", HttpStatus.CONFLICT,
            "履约状态不允许该操作"),

    /** 当前人员与订单的分配关系无效（改派后旧人员不能再推进履约）。 */
    FULFILLMENT_ASSIGNMENT_INVALID("FULFILLMENT_ASSIGNMENT_INVALID", HttpStatus.CONFLICT,
            "当前分配关系无效，无法继续履约"),

    /** 签到不在允许的时间范围内。 */
    FULFILLMENT_CHECK_IN_OUT_OF_WINDOW("FULFILLMENT_CHECK_IN_OUT_OF_WINDOW", HttpStatus.CONFLICT,
            "不在签到时间范围内"),

    /** 未签到不能开始服务。 */
    FULFILLMENT_START_BEFORE_CHECK_IN("FULFILLMENT_START_BEFORE_CHECK_IN", HttpStatus.CONFLICT,
            "请先签到再开始服务"),

    /** 必填检查项未完成，不能申请完成。 */
    FULFILLMENT_CHECKLIST_REQUIRED("FULFILLMENT_CHECKLIST_REQUIRED", HttpStatus.CONFLICT,
            "还有必做检查项未完成"),

    /** 需要证据的检查项缺少附件，不能申请完成。 */
    FULFILLMENT_EVIDENCE_REQUIRED("FULFILLMENT_EVIDENCE_REQUIRED", HttpStatus.CONFLICT,
            "还有检查项缺少服务证据"),

    /** 当前状态不允许申请完成。 */
    FULFILLMENT_COMPLETION_NOT_ALLOWED("FULFILLMENT_COMPLETION_NOT_ALLOWED", HttpStatus.CONFLICT,
            "当前状态不能申请完成"),

    /** 订单已完成，重复确认幂等拒绝。 */
    FULFILLMENT_ALREADY_COMPLETED("FULFILLMENT_ALREADY_COMPLETED", HttpStatus.CONFLICT, "订单已完成"),

    /** 客户确认已超过截止时间（等待自动确认任务处理）。 */
    FULFILLMENT_CONFIRMATION_EXPIRED("FULFILLMENT_CONFIRMATION_EXPIRED", HttpStatus.CONFLICT,
            "确认已超时，请等待系统自动确认"),

    /** 存在未处理的安全风险异常，不允许开始服务。 */
    FULFILLMENT_OPEN_SAFETY_EXCEPTION("FULFILLMENT_OPEN_SAFETY_EXCEPTION", HttpStatus.CONFLICT,
            "存在未处理的安全异常，无法开始服务"),

    // ---------------------------------------------------------------------------
    // aftersales（docs/modules/11 第 16 节）
    // ---------------------------------------------------------------------------

    /** 订单当前状态不允许评价。 */
    REVIEW_NOT_ALLOWED("REVIEW_NOT_ALLOWED", HttpStatus.CONFLICT, "订单完成后才能评价"),

    /** 该订单已存在有效评价。 */
    REVIEW_ALREADY_EXISTS("REVIEW_ALREADY_EXISTS", HttpStatus.CONFLICT, "该订单已评价过"),

    /** 评价维度编码不存在或未启用。 */
    REVIEW_DIMENSION_INVALID("REVIEW_DIMENSION_INVALID", HttpStatus.BAD_REQUEST, "评价维度不合法"),

    /** 维度分数超出 0-5 范围。 */
    REVIEW_SCORE_INVALID("REVIEW_SCORE_INVALID", HttpStatus.BAD_REQUEST, "评分必须在 0 到 5 之间"),

    /** 评价不存在。 */
    REVIEW_NOT_FOUND("REVIEW_NOT_FOUND", HttpStatus.NOT_FOUND, "评价不存在"),

    /** 订单当前状态不允许发起售后。 */
    AFTER_SALE_NOT_ALLOWED("AFTER_SALE_NOT_ALLOWED", HttpStatus.CONFLICT, "当前订单状态不能发起售后"),

    /** 售后工单不存在。 */
    AFTER_SALE_NOT_FOUND("AFTER_SALE_NOT_FOUND", HttpStatus.NOT_FOUND, "售后工单不存在"),

    /**
     * 售后工单不属于当前用户。
     *
     * <p>与 {@code BOOKING_NOT_OWNED} 同理：统一回"不存在"防探测。</p>
     */
    AFTER_SALE_NOT_OWNED("AFTER_SALE_NOT_OWNED", HttpStatus.NOT_FOUND, "售后工单不存在"),

    /** 工单当前状态不允许该操作。 */
    AFTER_SALE_STATUS_CONFLICT("AFTER_SALE_STATUS_CONFLICT", HttpStatus.CONFLICT,
            "售后工单状态不允许该操作"),

    /** 关闭 / 解决工单必须填写处理结论。 */
    AFTER_SALE_RESOLUTION_REQUIRED("AFTER_SALE_RESOLUTION_REQUIRED", HttpStatus.BAD_REQUEST,
            "请填写处理结论"),

    /** 处理结论为退款时必须关联退款记录。 */
    AFTER_SALE_REFUND_LINK_REQUIRED("AFTER_SALE_REFUND_LINK_REQUIRED", HttpStatus.CONFLICT,
            "退款结论必须关联退款记录"),

    /** 附件类型不合法或文件不存在。 */
    AFTER_SALE_ATTACHMENT_INVALID("AFTER_SALE_ATTACHMENT_INVALID", HttpStatus.BAD_REQUEST,
            "售后附件不合法"),

    /** 工单超出处理时限（标记用）。 */
    AFTER_SALE_SLA_EXPIRED("AFTER_SALE_SLA_EXPIRED", HttpStatus.CONFLICT, "工单已超出处理时限"),

    /** 人员质量汇总重算失败。 */
    QUALITY_STAT_RECALCULATION_FAILED("QUALITY_STAT_RECALCULATION_FAILED",
            HttpStatus.INTERNAL_SERVER_ERROR, "质量统计重算失败"),

    /** 相同幂等键但请求内容不同。 */
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", HttpStatus.CONFLICT, "幂等键冲突，请勿修改请求内容后重试"),

    /** 文件类型、大小或内容校验不通过。 */
    FILE_UPLOAD_INVALID("FILE_UPLOAD_INVALID", HttpStatus.BAD_REQUEST, "文件不符合上传要求"),

    /** 触发限流。 */
    RATE_LIMITED("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"),

    /** 对象存储或消息中间件等外部依赖暂不可用。 */
    INTEGRATION_UNAVAILABLE("INTEGRATION_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "外部依赖暂不可用"),

    /** 未预期异常，对外只暴露通用提示。 */
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");

    private final String code;

    private final HttpStatus httpStatus;

    private final String defaultMessage;

    ErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
