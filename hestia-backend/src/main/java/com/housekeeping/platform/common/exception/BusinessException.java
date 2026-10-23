package com.housekeeping.platform.common.exception;

import com.housekeeping.platform.common.api.ErrorCode;

/**
 * 业务异常。
 *
 * <p>只用于"可以预期、需要明确告知调用方"的失败，例如状态冲突、权限不足、参数语义错误。
 * 未预期异常（空指针、SQL 错误等）不允许包装成业务异常，必须交给全局处理器记录完整堆栈。</p>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /**
     * 使用错误码的默认提示构造异常。
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义提示构造异常。
     *
     * @param errorCode 错误码
     * @param message   面向调用方的补充说明，不得包含敏感信息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义提示并保留底层原因，便于日志定位；底层原因不会返回给客户端。
     *
     * @param errorCode 错误码
     * @param message   面向调用方的补充说明
     * @param cause     原始异常
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
