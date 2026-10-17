package com.mufeng.common.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全系统标准化错误码枚举
 * 规范：200-成功；4xx~5xx-通用系统与并发错误；1xxx-营销算价；2xxx-双模库存；3xxx-交易履约；4xxx-清结算对账
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {

    // ==================== 0. 通用成功与系统级错误 ====================
    SUCCESS(200, "操作成功"),
    PARAM_VALID_ERROR(400, "请求参数校验失败"),
    IDEMPOTENT_CONFLICT(409, "请求正在处理中，请勿重复提交"),
    LOCK_ACQUIRE_TIMEOUT(429, "当前操作人数过多，请稍后再试"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后再试"),
    RPC_INVOKE_ERROR(503, "远程服务调用失败"),

    // ==================== 1. 营销算价域 (1001 ~ 1999) ====================
    COUPON_TEMPLATE_NOT_FOUND(1001, "优惠券模板不存在或已下架"),
    COUPON_STOCK_EXHAUSTED(1002, "优惠券已被领完"),
    COUPON_LIMIT_EXCEEDED(1003, "已达到该优惠券每人限领上限"),
    USER_COUPON_NOT_AVAILABLE(1004, "该优惠券不可用或已被锁定"),
    COUPON_THRESHOLD_NOT_MET(1005, "订单金额未达到优惠券使用门槛"),
    COUPON_MUTEX_CONFLICT(1006, "所选优惠券之间存在互斥规则，不可叠加使用"),
    APPORTION_AMOUNT_ERROR(1007, "营销金额分摊计算异常"),

    // ==================== 2. 双模库存域 (2001 ~ 2999) ====================
    SKU_NOT_FOUND(2001, "商品SKU不存在"),
    NORMAL_STOCK_INSUFFICIENT(2002, "商品日常可售库存不足"),
    SECKILL_STOCK_SOLD_OUT(2003, "当前秒杀商品已售罄"),
    SECKILL_REPEAT_BUY(2004, "您已参与过本次秒杀活动，请勿重复抢购"),
    STOCK_ALLOCATION_FAILED(2005, "秒杀活动库存划拨失败，可用库存不足"),
    STOCK_DEDUCT_LOG_NOT_FOUND(2006, "未找到对应的库存预扣流水记录"),

    // ==================== 3. 交易履约域 (3001 ~ 3999) ====================
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_ILLEGAL(3002, "当前订单状态不允许执行此操作"),
    ORDER_PAY_EXPIRED(3003, "订单已超过支付有效期，交易已关闭"),
    REFUND_AMOUNT_OVERFLOW(3004, "申请退款金额超出可退上限"),

    // ==================== 4. 清结算与对账域 (4001 ~ 4999) ====================
    SETTLEMENT_BILL_EXISTED(4001, "该子订单已生成结算单，请勿重复清分"),
    RECONCILIATION_TASK_RUNNING(4002, "当前日期的对账任务正在执行中"),
    SETTLEMENT_FROZEN_BY_DIFF(4003, "该笔订单存在对账差错，资金结算已冻结");

    private final Integer code;
    private final String message;
}