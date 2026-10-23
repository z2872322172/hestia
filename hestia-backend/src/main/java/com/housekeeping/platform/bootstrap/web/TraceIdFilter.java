package com.housekeeping.platform.bootstrap.web;

import com.housekeeping.platform.common.api.RequestContext;
import com.housekeeping.platform.common.id.IdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路标识过滤器：为每个请求建立 traceId，并回写到响应头。
 *
 * <p>为什么必须最早执行：后续的鉴权、SQL 日志、MQ 发送日志都要带 traceId，
 * 一旦有环节在过滤器之前记录日志就会出现断链。</p>
 *
 * <p>只接受长度合法且字符安全的入站 traceId（网关/前端透传），否则重新生成，
 * 防止把超长或带换行的值写进日志造成日志注入。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final int MAX_TRACE_ID_LENGTH = 64;

    private static final String SAFE_TRACE_ID_PATTERN = "[A-Za-z0-9_-]+";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String inboundTraceId = request.getHeader(RequestContext.TRACE_ID_HEADER);
        String traceId = isValid(inboundTraceId) ? inboundTraceId : IdGenerator.nextId();
        RequestContext.setTraceId(traceId);
        response.setHeader(RequestContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程会被复用，必须在请求结束时清理，否则下一个请求可能继承上一个 traceId
            RequestContext.clear();
        }
    }

    private boolean isValid(String traceId) {
        return traceId != null
                && !traceId.isBlank()
                && traceId.length() <= MAX_TRACE_ID_LENGTH
                && traceId.matches(SAFE_TRACE_ID_PATTERN);
    }
}
