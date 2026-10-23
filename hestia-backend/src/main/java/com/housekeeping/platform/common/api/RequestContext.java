package com.housekeeping.platform.common.api;

import org.slf4j.MDC;

/**
 * 单次请求的上下文持有者（当前只维护 traceId）。
 *
 * <p>traceId 同时写入 {@link MDC}，日志模板 {@code %X{traceId}} 直接取用；
 * 由于 Servlet 线程模型下请求之间会复用线程，必须在请求结束时清理，避免 traceId 串号。</p>
 */
public final class RequestContext {

    /** 请求/响应头中的链路标识字段名。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 认证失败原因在请求属性中的键。
     *
     * <p>过滤器不直接写响应，只把错误码放在这里，由 {@code AuthenticationEntryPoint}
     * 统一输出 JSON：这样"没带令牌"与"令牌失效"的响应结构必然一致，
     * 不会出现一处返回 {@code AUTH_REQUIRED}、另一处返回裸 401 的情况。</p>
     */
    public static final String AUTH_ERROR_ATTRIBUTE = "hestia.auth.errorCode";

    private static final String MDC_TRACE_ID = "traceId";

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private RequestContext() {
    }

    /**
     * 绑定当前线程的 traceId。
     *
     * @param traceId 链路标识，不能为空
     */
    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
        MDC.put(MDC_TRACE_ID, traceId);
    }

    /**
     * 获取当前线程的 traceId。
     *
     * @return traceId；非请求线程（如定时任务）可能返回 {@code null}
     */
    public static String getTraceId() {
        return TRACE_ID.get();
    }

    /**
     * 清理当前线程上下文，必须与 {@link #setTraceId(String)} 成对调用。
     */
    public static void clear() {
        TRACE_ID.remove();
        MDC.remove(MDC_TRACE_ID);
    }
}
