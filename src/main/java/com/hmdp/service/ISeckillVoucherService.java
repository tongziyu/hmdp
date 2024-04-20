package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.Voucher;
import com.hmdp.utils.RedisConstants;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface ISeckillVoucherService extends IService<SeckillVoucher> {

    /**
     * 抢购优惠券
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 抢购优惠券[乐观锁]
     * @param voucherId
     * @return
     */
    public Result seckillVoucherOptimisticLock(Long voucherId);

    /**
     * 抢购优惠券 悲观锁
     * @param voucherId
     * @return
     */
    Result seckillVoucherPessimisticLock(Long voucherId);

    /**
     * 创建优惠券订单
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId);

    /**
     * redis分布式锁
     * @param voucherId
     * @return
     */
    public Result seckillVoucherRedisLock(Long voucherId) throws InterruptedException;


}
