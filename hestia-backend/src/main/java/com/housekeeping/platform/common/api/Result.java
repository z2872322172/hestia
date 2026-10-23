package com.housekeeping.platform.common.api;

import com.housekeeping.platform.common.id.IdGenerator;

/**
 * 统一响应结构。
 *
 * <p>契约固定为 {@code code / message / data / traceId} 四个字段（docs/07 第 13.2 节），
 * 前端只需要判断 {@code code == "OK"}，错误信息一律走 {@code code} 而不是 HTTP 文本。</p>
 *
 * <p>{@code traceId} 由 {@link RequestContext} 自动填充，便于把前端报错和服务端日志对上。</p>
 *
 * @param <T> 业务数据类型
 */
public record Result<T>(String code, String message, T data, String traceId) {

    /** 成功码，与错误码枚举共用同一命名空间。 */
    public static final String SUCCESS_CODE = "OK";

    private static final String SUCCESS_MESSAGE = "success";

    /**
     * 构造成功响应。
     *
     * @param data 业务数据，可为 {@code null}
     * @param <T>  数据类型
     * @return 统一响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, currentTraceId());
    }

    /**
     * 构造无数据的成功响应。
     *
     * @param <T> 数据类型
     * @return 统一响应
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构造失败响应。
     *
     * @param code    错误码
     * @param message 面向用户的错误说明
     * @param <T>     数据类型
     * @return 统一响应
     */
    public static <T> Result<T> failure(String code, String message) {
        return new Result<>(code, message, null, currentTraceId());
    }

    /**
     * 取当前链路 ID；非请求线程没有 traceId 时现场生成一个，
     * 保证响应结构中的该字段永远不为空。
     */
    private static String currentTraceId() {
        String traceId = RequestContext.getTraceId();
        return traceId != null ? traceId : IdGenerator.nextId();
    }
}
