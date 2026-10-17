package com.mufeng.common.exception;

import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常统一拦截处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. 拦截业务异常 BizException
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("[业务异常拦截] URI: {}, Code: {}, Msg: {}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 2. 拦截 @Valid / @Validated 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errorDetail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] URI: {}, Detail: {}", request.getRequestURI(), errorDetail);
        return Result.fail(ErrorCodeEnum.PARAM_VALID_ERROR, errorDetail);
    }

    /**
     * 3. 拦截未知兜底异常，防止堆栈直接暴露给外部
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknownException(Exception e, HttpServletRequest request) {
        log.error("[系统未知异常] URI: {}", request.getRequestURI(), e);
        return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
    }
}