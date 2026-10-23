package com.housekeeping.platform.bootstrap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.RequestContext;
import com.housekeeping.platform.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证入口：返回统一 JSON，而不是 Spring Security 默认的 HTML 登录页。
 *
 * <p>前后端分离场景下，浏览器跳转登录页会让前端拿到一段无法解析的 HTML，
 * 因此必须自己输出与业务接口一致的结构。</p>
 *
 * <p>错误码优先取 {@link RequestContext#AUTH_ERROR_ATTRIBUTE}：JWT 过滤器会把
 * "过期 / 停用 / 锁定 / 角色被撤" 等原因放在请求属性里，这里原样透出，
 * 前端才能区分"重新登录"和"联系管理员"；没有该属性时才是普通的未登录。</p>
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        ErrorCode errorCode = resolveErrorCode(request);
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                Result.failure(errorCode.getCode(), errorCode.getDefaultMessage()));
    }

    private ErrorCode resolveErrorCode(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestContext.AUTH_ERROR_ATTRIBUTE);
        return attribute instanceof ErrorCode errorCode ? errorCode : ErrorCode.AUTH_REQUIRED;
    }
}
