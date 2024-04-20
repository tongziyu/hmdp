package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    private VoucherOrderMapper voucherOrderMapper;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final Object lock = new Object();



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
            ISeckillVoucherService proxy =(ISeckillVoucherService) AopContext.currentProxy();
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


    /**
     * 抢购优惠券,[分布式锁版本]
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucherRedisLock(Long voucherId) throws InterruptedException {
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
        // 使用Redisson 实现分布式锁
        RLock redissonClientLock = redissonClient.getLock("lock:order:" + id);

        // 第一个参数:重试时间  第二个参数:key的TTL  第三个参数:时间单位
        boolean success = redissonClientLock.tryLock(1, 10, TimeUnit.SECONDS);


        // 创建分布式锁对象
        // SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:"+id,stringRedisTemplate);
        //boolean success = simpleRedisLock.tryLock(20);

        if (!success){
            return Result.fail("不能重复下单");
        }
        try {
            // 获取代理对象,防止事务失效
            // 需要添加aspectjweaver 依赖
            // 主启动类上添加 @EnableAspectJAutoProxy(exposeProxy = true)
            ISeckillVoucherService proxy =(ISeckillVoucherService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            // 释放锁
            redissonClientLock.unlock();
        }
    }
}
