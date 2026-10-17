package com.mufeng.marketing.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.marketing.infrastructure.persistence.po.UserCouponPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户优惠券实例 Mapper 接口
 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCouponPO> {

    /**
     * 提单原子锁券：仅当状态为 1(未使用) 且未过期时，更新为 2(已锁定)
     */
    @Update("UPDATE t_user_coupon " +
            "SET status = 2, locked_parent_order_no = #{parentOrderNo}, use_time = NOW(), version = version + 1 " +
            "WHERE id = #{userCouponId} AND user_id = #{userId} AND status = 1 AND expire_time > NOW()")
    int lockCouponCas(@Param("userCouponId") Long userCouponId,
                      @Param("userId") Long userId,
                      @Param("parentOrderNo") Long parentOrderNo);

    /**
     * 关单/全退原子解券：将状态为 2(已锁定) 的优惠券恢复为 1(未使用)
     */
    @Update("UPDATE t_user_coupon " +
            "SET status = 1, locked_parent_order_no = NULL, use_time = NULL, version = version + 1 " +
            "WHERE user_id = #{userId} AND locked_parent_order_no = #{parentOrderNo} AND status = 2")
    int releaseCouponCas(@Param("userId") Long userId,
                         @Param("parentOrderNo") Long parentOrderNo);
}