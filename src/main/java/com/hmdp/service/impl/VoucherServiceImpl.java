package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import net.sf.jsqlparser.util.validation.validator.SelectValidator;
import org.springframework.aop.framework.AopContext;
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

    private static final Object lock = new Object();

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
    /**
     * 抢购优惠券 [乐观锁]
     * 添加一人一单功能
     * @param voucherId
     * @return
     */
    @Override

    public Result seckillVoucherOptimisticLock(Long voucherId) {
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
        // 解决一人一单超卖问题
        Long id = UserHolder.getUser().getId();

        synchronized (id.toString().intern()){
            // 获取代理对象,防止事务失效
            // 需要添加aspectjweaver 依赖
            // 主启动类上添加 @EnableAspectJAutoProxy(exposeProxy = true)
            IVoucherService proxy = (IVoucherService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }

    }

    /**
     * 创建优惠券订单
     * @param voucherId
     * @return
     */
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId){
        // 解决一人一单超卖问题
        Long id = UserHolder.getUser().getId();

        // 一人一单:
        // 查询订单,一个人对应一个订单的id只能有一条记录,如果已经有记录了,则不能再下单了
        QueryWrapper queryWrapper = new QueryWrapper<VoucherOrder>();
        queryWrapper.eq("user_id",id);
        queryWrapper.eq("voucher_id",voucherId);

        Integer count = voucherOrderMapper.selectCount(queryWrapper);


        if (count > 0){
            return Result.fail("每人只能下一单优惠券");
        }


        // 乐观锁
        // 减扣库存,并且判断查询出来时库存是否已经发生变化,如果发生变化了,则返回错误信息,如果没发生变化,则进行扣减
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                // where id = id and stock > 0 在进行减扣库存,大大提高了项目的可行性
                .gt("stock", 0).update();


        if (!update){
            return Result.fail("库存不足!");
        }

        // 5.创建订单,并返回id
        // 使用全局ID生成器,生成id
        long order = redisIdWorker.nextId("order");

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(order);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());

        voucherOrderMapper.insert(voucherOrder);
        return Result.ok(order);
    }

    /**
     * 抢购优惠券,悲观锁版本
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucherPessimisticLock(Long voucherId){
        Long id = UserHolder.getUser().getId();
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
        // 悲观锁 直接加锁
        synchronized (id.toString().intern()){
            // 3.如果开始了 判断库存是否充足
            // 秒杀活动一开始,判断库存
            Integer stock = seckillVoucher.getStock();
            // 4.如果库存充足,则减扣库存,并创建订单

            if (stock < 1){
                return Result.fail("库存不足");
            }

            // 减扣库存,并且判断查询出来时库存是否已经发生变化,如果发生变化了,则返回错误信息,如果没发生变化,则进行扣减
            boolean update = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", seckillVoucher.getVoucherId())
                    .update();


            if (!update){
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
}
