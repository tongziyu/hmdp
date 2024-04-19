package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    private VoucherOrderMapper voucherOrderMapper;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
    }

    /**
     * 抢购优惠券
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠卷信息
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);

        if (seckillVoucher == null){
            return Result.fail("优惠券信息不存在!!");
        }
        // 2.判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();

        LocalDateTime endTime = seckillVoucher.getEndTime();

        LocalDateTime now = LocalDateTime.now();

        // 暂未开始
        if (beginTime.isAfter(now)){
            return Result.fail("秒杀暂未开始");
        }

        // 已经结束
        if (endTime.isBefore(now)){
            return Result.fail("秒杀已经结束");
        }


        // 3.如果开始了 判断库存是否充足
        // 秒杀活动一开始,判断库存
        Integer stock = seckillVoucher.getStock();
        // 4.如果库存充足,则减扣库存,并创建订单

        if (stock < 1){
            return Result.fail("库存不足");
        }

        // 减扣库存
        seckillVoucher.setStock(seckillVoucher.getStock()-1);
        int i = seckillVoucherMapper.updateById(seckillVoucher);

        if (i != 1){
            return Result.fail("库存不足!");
        }

        // 5.创建订单,并返回id
        // 使用全局ID生成器,生成id
        long order = redisIdWorker.nextId("order");

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(order);
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
        voucherOrder.setUserId(UserHolder.getUser().getId());

        voucherOrderMapper.insert(voucherOrder);

        return Result.ok(order);

    }
}
