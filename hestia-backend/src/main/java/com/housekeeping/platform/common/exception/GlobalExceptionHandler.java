package com.housekeeping.platform.common.exception;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.RequestContext;
import com.housekeeping.platform.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：把各类异常翻译成统一响应，保证客户端永远只看到 {@code code / message / traceId}。
 *
 * <p>两条硬约束：</p>
 * <ol>
 *   <li>未预期异常只记录服务端日志（含堆栈），响应体不得包含堆栈、SQL 或类名；</li>
 *   <li>日志中不得输出请求体全文，避免手机号、地址、密码进入日志。</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 校验错误拼接上限，避免超长参数把响应体撑爆。 */
    private static final int MAX_VALIDATION_MESSAGE_LENGTH = 500;

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 带业务错误码与对应 HTTP 状态的统一响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        // 业务异常是预期分支，用 WARN 记录，不打印堆栈以免污染日志
        LOG.warn("业务异常 code={} message={} traceId={}",
                errorCode.getCode(), exception.getMessage(), RequestContext.getTraceId());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.failure(errorCode.getCode(), exception.getMessage()));
    }

    /**
     * 处理请求体参数校验失败。
     *
     * @param exception 校验异常
     * @return 400 与校验失败明细
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return badRequest(detail);
    }

    /**
     * 处理方法参数与路径变量校验失败。
     *
     * @param exception 校验异常
     * @return 400 与校验失败明细
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        String detail = exception.getConstraintViolations().stream()
                .map(this::formatConstraintViolation)
                .collect(Collectors.joining("; "));
        return badRequest(detail);
    }

    /**
     * 处理缺少必填查询参数。
     *
     * @param exception 参数缺失异常
     * @return 400
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return badRequest("缺少必填参数：" + exception.getParameterName());
    }

    /**
     * 处理参数类型不匹配。
     *
     * @param exception 类型不匹配异常
     * @return 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return badRequest("参数格式错误：" + exception.getName());
    }

    /**
     * 处理请求体无法解析（JSON 语法错误、枚举值非法等）。
     *
     * @param exception 解析异常
     * @return 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException exception) {
        return badRequest("请求体格式错误");
    }

    /**
     * 处理上传文件超过大小限制。
     *
     * <p>大小限制在容器层就被拦截，这里转换成业务错误码，避免前端拿到裸 500。</p>
     *
     * @param exception 超限异常
     * @return 400 与文件上传错误码
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(ErrorCode.FILE_UPLOAD_INVALID.getHttpStatus())
                .body(Result.failure(ErrorCode.FILE_UPLOAD_INVALID.getCode(), "文件大小超过限制"));
    }

    /**
     * 处理权限不足。
     *
     * @param exception 权限异常
     * @return 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException exception) {
        LOG.warn("权限不足 uri 访问被拒绝 traceId={}", RequestContext.getTraceId());
        return ResponseEntity.status(ErrorCode.AUTH_FORBIDDEN.getHttpStatus())
                .body(Result.failure(ErrorCode.AUTH_FORBIDDEN.getCode(), ErrorCode.AUTH_FORBIDDEN.getDefaultMessage()));
    }

    /**
     * 处理静态资源/未知路径 404。
     *
     * @param exception 资源不存在异常
     * @return 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(Result.failure(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage()));
    }

    /**
     * 处理 HTTP 方法不支持。
     *
     * @param exception 方法不支持异常
     * @return 405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(405)
                .body(Result.failure(ErrorCode.VALIDATION_FAILED.getCode(), "不支持的请求方法：" + exception.getMethod()));
    }

    /**
     * 兜底处理未预期异常。
     *
     * <p>必须记录完整堆栈，但响应体只返回通用提示，绝不把内部实现细节暴露给客户端。</p>
     *
     * @param exception 未预期异常
     * @param request   当前请求，仅用于记录路径，不记录请求体
     * @return 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOG.error("未预期异常 uri={} traceId={}", request.getRequestURI(), RequestContext.getTraceId(), exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }

    private ResponseEntity<Result<Void>> badRequest(String detail) {
        String message = detail == null || detail.isBlank() ? ErrorCode.VALIDATION_FAILED.getDefaultMessage() : detail;
        if (message.length() > MAX_VALIDATION_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_VALIDATION_MESSAGE_LENGTH);
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(Result.failure(ErrorCode.VALIDATION_FAILED.getCode(), message));
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }

    private String formatConstraintViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + " " + violation.getMessage();
    }
}
