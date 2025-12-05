package com.riskwarning.org.service;

import com.riskwarning.common.dto.*;
import com.riskwarning.common.po.user.User;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param registerRequest 注册请求
     * @return 用户信息
     */
    UserResponse register(RegisterRequest registerRequest);

    /**
     * 用户登录
     *
     * @param loginRequest 登录请求
     * @return 登录响应（包含token和用户信息）
     */
    LoginResponse login(LoginRequest loginRequest);

    /**
     * 根据ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserResponse getUserById(Long userId);

    /**
     * 根据邮箱获取用户信息
     *
     * @param email 邮箱
     * @return 用户信息
     */
    User getUserByEmail(String email);

    /**
     * 更新用户信息
     *
     * @param userId 用户ID
     * @param updateRequest 更新请求
     * @return 更新后的用户信息
     */
    UserResponse updateUser(Long userId, UserResponse updateRequest);

    /**
     * 验证用户密码
     *
     * @param user 用户信息
     * @param rawPassword 原始密码
     * @return 是否验证成功
     */
    boolean validatePassword(User user, String rawPassword);
}
