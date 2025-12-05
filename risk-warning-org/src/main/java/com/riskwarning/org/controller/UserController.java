package com.riskwarning.org.controller;

import com.riskwarning.common.annotation.AuthRequired;
import com.riskwarning.common.context.UserContext;
import com.riskwarning.common.dto.*;
import com.riskwarning.common.result.Result;
import com.riskwarning.org.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 用户认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     *
     * @param registerRequest 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            UserResponse userResponse = userService.register(registerRequest);
            return Result.success(userResponse);
        } catch (Exception e) {
            log.error("用户注册失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 用户登录
     *
     * @param loginRequest 登录请求
     * @return ���录结果（包含token和用户信息）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            LoginResponse loginResponse = userService.login(loginRequest);
            return Result.success(loginResponse);
        } catch (Exception e) {
            log.error("用户登录失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 获取当前用户信息
     *
     * @param request HTTP请求
     * @return 用户信息
     */
    @GetMapping("/profile")
    @AuthRequired
    public Result<UserResponse> getCurrentUser(HttpServletRequest request) {
        try {
            UserResponse userResponse = userService.getUserById(UserContext.getUser().getId());
            return Result.success(userResponse);
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户��息
     */
    @GetMapping("/{userId}")
    public Result<UserResponse> getUserById(@PathVariable Long userId) {
        try {
            UserResponse userResponse = userService.getUserById(userId);
            return Result.success(userResponse);
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 更新用户信息
     *
     * @param updateRequest 更新请求
     * @param request HTTP请求
     * @return 更新结果
     */
    @PutMapping("/profile")
    @AuthRequired
    public Result<UserResponse> updateProfile(@RequestBody UserResponse updateRequest,
                                                  HttpServletRequest request) {
        try {
            UserResponse userResponse = userService.updateUser(UserContext.getUser().getId(), updateRequest);
            return Result.success(userResponse);
        } catch (Exception e) {
            log.error("更新用户信息失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }
}




