package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.processing.SupportedSourceVersion;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @Description:
 * @Author: Ian
 * @Date: 2024/4/15 11:57
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取session
        HttpSession session = request.getSession();

        // 2.判断用户是否存在
        Object user = session.getAttribute("user");

        // 3.用户不存在直接拦截
        if (user == null){
            response.setStatus(401);
            return false;
        }

        log.info("拦截器的userDTO: {}",user);
        UserHolder.saveUser((UserDTO) user);
        log.info("已经放行!!!!!!!!!");
        // 放行
        return true;
    }
}
