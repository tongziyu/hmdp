package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Description:
 * @Author: Ian
 * @Date: 2024/4/15 19:23
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从请求头中获取token信息
        String authorization = request.getHeader("authorization");

        if (StrUtil.isBlank(authorization)) {

            return true;
        }

        // 2.从数据库中将token对应的value查询出来

        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + authorization);

        if (entries.isEmpty()){
            return true;
        }

        // 将map转换成java对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);

        // 将用户存入ThreadLocal中
        UserHolder.saveUser(userDTO);

        //刷新redis中token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + authorization,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 放行
        return true;
    }
}
