package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.ognl.Token;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 发送验证码步骤

        // 1.校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);

        // 2.如果手机号格式不正确则返回错误信息
        if (phoneInvalid){
            return Result.fail("手机号格式不正确,请重新输入!");
        }
        // 3.如果手机号正确则生成验证码,发送验证码
        String code = RandomUtil.randomNumbers(6);
        log.info("系统生成的验证码:{}",code);

        // 4.将验证码保存进Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        session.setAttribute("code",code);

        // 5.发送验证码
        log.info("验证码发送成功,验证码为:{}",code);

        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式不正确,请重新输入!");
        }

        // 2.校验验证码
        String PreCode = loginForm.getCode();

        // 从redis中获取验证码
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());

        if (code == null || !PreCode.equals(code)){
            return Result.fail("验证码不正确!");
        }

        // 3.查询用户是否存在

        User user = query().eq("phone", loginForm.getPhone()).one();

        // 4.如果不存在这新建用户
        if (user == null){
            // 保存新用户
            user = createUserWithPhone(loginForm.getPhone());
        }

        // 5.将用户保存进redis中

        // 生成UUID 简洁版
        String token = UUID.randomUUID().toString(true);

        log.info("生成的UUID为:{}",token);

        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        // 将对象转换成HashMap,存入Redis中 以Hash的数据类型存入
        Map<String, Object> userHashMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 将user存入redis
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token,userHashMap);

        // 设置过期时间为30分钟
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);


        // 6.返回成功结果, 并将token返回只有这样前端每次访问 才能携带token
        return Result.ok(token);
    }


    @Override
    public Result logout() {
        UserHolder.removeUser();
        return Result.ok();
    }

    /**
     * 通过手机号创建用户
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        log.info("通过手机号新创建的用户为:{}",user);
        return user;
    }


}
