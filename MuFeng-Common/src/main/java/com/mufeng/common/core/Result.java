package com.mufeng.common.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全系统统一响应结果封装 (HTTP & OpenFeign RPC 通用)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态码 (200 表示成功)
     */
    private Integer code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 响应数据载荷
     */
    private T data;

    /**
     * 响应时间戳
     */
    private Long timestamp;

    public static <T> Result<T> success() {
        return new Result<>(
                ErrorCodeEnum.SUCCESS.getCode(),
                ErrorCodeEnum.SUCCESS.getMessage(),
                null,
                System.currentTimeMillis()
        );
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(
                ErrorCodeEnum.SUCCESS.getCode(),
                ErrorCodeEnum.SUCCESS.getMessage(),
                data,
                System.currentTimeMillis()
        );
    }

    public static <T> Result<T> fail(ErrorCodeEnum errorCode) {
        return new Result<>(
                errorCode.getCode(),
                errorCode.getMessage(),
                null,
                System.currentTimeMillis()
        );
    }

    public static <T> Result<T> fail(ErrorCodeEnum errorCode, String customMessage) {
        return new Result<>(
                errorCode.getCode(),
                customMessage,
                null,
                System.currentTimeMillis()
        );
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }

    /**
     * 供 OpenFeign 客户端快速校验远程调用是否成功
     */
    public boolean isSuccess() {
        return ErrorCodeEnum.SUCCESS.getCode().equals(this.code);
    }
}