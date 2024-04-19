package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);

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
}
