package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

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
     * 阻塞队列,长度为1024*1024
     */
    private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024 * 1024);


    /**
     * 线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * @PostConstruct: 该注解会在这个类初始化完成后,执行该方法
     */
    @PostConstruct
    private void init(){
        // 提交任务
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 处理下单的内部类
     */
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true){
                try {
                    VoucherOrder voucherOrder = orderTask.take();
                    // 下单方法
                    handlerVoucherOrder(voucherOrder);

                } catch (Exception e) {
                    log.error("处理下单异常:{}",e);
                }
            }
        }
    }

    /**
     * 处理下单的方法
     * @param voucherOrder
     */
    public void handlerVoucherOrder(VoucherOrder voucherOrder){
        // 1.获取用户
        Long userId = voucherOrder.getUserId();
        // 2.获取锁对象
        RLock redissonClientLock = redissonClient.getLock("lock:order:" + userId);

        // 3.拿到锁
        boolean b = redissonClientLock.tryLock();

        // 获取锁失败则返回错误
        if (!b){
            log.error("不允许重复下单");
            return;
        }
        // 成功则save订单
        try {
            proxy.createVoucherOrderAsy(voucherOrder);
        }finally {
            redissonClientLock.unlock();
        }
    }

    /**
     * 创建优惠券订单
     * @return
     */
    @Transactional
    @Override
    public void createVoucherOrderAsy(VoucherOrder voucherOrder){
        // 一人一单:
        // 查询订单,一个人对应一个订单的id只能有一条记录,如果已经有记录了,则不能再下单了
        QueryWrapper queryWrapper = new QueryWrapper<VoucherOrder>();
        queryWrapper.eq("user_id",voucherOrder.getUserId());
        queryWrapper.eq("voucher_id",voucherOrder.getVoucherId());

        Integer count = voucherOrderMapper.selectCount(queryWrapper);

        if (count > 0){
            log.error("每人只能下一单优惠券");
            return;
        }

        // 乐观锁
        // 减扣库存,并且判断查询出来时库存是否已经发生变化,如果发生变化了,则返回错误信息,如果没发生变化,则进行扣减
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                // where id = id and stock > 0 在进行减扣库存,大大提高了项目的可行性
                .gt("stock", 0).update();


        if (!update){
            log.error("库存不足!");
            return ;
        }

        // 5.创建订单,并返回id

        voucherOrderMapper.insert(voucherOrder);

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

    // 加载Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private SeckillVoucherServiceImpl proxy;

    /**
     * 抢购优惠券,[分布式锁版本] 使用lua脚本优化操作,减少数据库查询操作,提高接口效率
     * @param voucherId
     * @return
     */
    public Result seckillVoucherRedisLock2(Long voucherId) throws InterruptedException {
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute( //调用execute方法，返回值
                SECKILL_SCRIPT, //加载的模板对象
                Collections.emptyList(),    //键参数
                voucherId.toString(),    //值参数1
                UserHolder.getUser().getId().toString()    //值参数2
        );

        // 2.判断返回结果,如果不是0,则不能进行秒杀
        int excuteInt = result.intValue();
        if (excuteInt != 0){
            return Result.fail(excuteInt == 1? "库存不足":"不能重复下单");
        }
        // 3.如果是0,则表示可以进行秒杀
        // 开启异步线程,实现下单功能

        long orderId = redisIdWorker.nextId("order" + voucherId);

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());

        // 将订单信息放入阻塞队列
        orderTask.add(voucherOrder);

        // 开启异步线程进行下单操作.
        proxy = (SeckillVoucherServiceImpl) AopContext.currentProxy();
        return Result.ok(orderId);
    }
}
