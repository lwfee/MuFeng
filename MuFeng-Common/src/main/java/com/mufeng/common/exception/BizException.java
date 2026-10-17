package com.mufeng.common.exception;

import com.mufeng.common.core.ErrorCodeEnum;
import lombok.Getter;

import java.io.Serial;

/**
 * 自定义业务运行时异常 (自动触发 Spring 本地事务回滚)
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Integer code;

    public BizException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCodeEnum errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}