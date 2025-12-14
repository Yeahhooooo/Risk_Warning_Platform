package com.riskwarning.common.aspect;


import com.riskwarning.common.context.UserContext;
import com.riskwarning.common.po.user.User;
import com.riskwarning.common.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
@Slf4j
public class AuthAspect {

    @Autowired
    private JwtUtils jwtUtils;

    @PersistenceContext
    private EntityManager em;

    @Around("@annotation(com.riskwarning.common.annotation.AuthRequired)")
    public Object aroundAuthRequired(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new RuntimeException("无法获取请求信息");
        }

        HttpServletRequest request = attributes.getRequest();

        // 读取 header
        Long userId = jwtUtils.getUserIdFromToken(request.getHeader("Authorization"));
        if (userId == null) {
            log.warn("AuthAspect: 缺少 user_id 请求头，禁止访问");
            throw new RuntimeException("缺少 user_id 请求头，禁止访问");
        }

        log.info("AuthAspect: 通过 user_id={} 进行权限验证", userId);

        // TODO: 查询用户信息，放入UserContext
        User user = em.find(User.class, userId);
        if (user == null) {
            log.warn("AuthAspect: user not found for id={}, 禁止访问", userId);
            throw new RuntimeException("user not found: " + userId);
        }

        UserContext.setUser(user);
        try {
            return joinPoint.proceed();
        } finally {
            UserContext.clear();
        }
    }

    public static void main(String[] args) {
        System.out.println("00011001".compareTo("011001"));
    }
}
