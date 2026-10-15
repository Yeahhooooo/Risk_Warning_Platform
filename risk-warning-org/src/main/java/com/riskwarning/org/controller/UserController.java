package com.riskwarning.org.controller;

import com.riskwarning.common.annotation.AuthRequired;
import com.riskwarning.common.context.UserContext;
import com.riskwarning.common.dto.*;
import com.riskwarning.common.result.Result;
import com.riskwarning.org.config.SsoProperties;
import com.riskwarning.org.service.SsoService;
import com.riskwarning.org.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SsoService ssoService;
    private final SsoProperties ssoProperties;

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
     * 获取统一身份认证跳转地址（前端可直接 location.href）
     */
    @GetMapping("/sso/authorize-url")
    public Result<Map<String, String>> ssoAuthorizeUrl(@RequestParam(required = false) String state) {
        Map<String, String> data = new HashMap<>(2);
        data.put("url", ssoService.buildAuthorizeUrl(state));
        return Result.success(data);
    }

    /**
     * 统一身份认证回调：用 code 换本地 JWT。
     * 默认 302 到前端并带 ?token=；加 format=json 则直接返回 LoginResponse（便于联调）。
     */
    @GetMapping("/sso/callback")
    public Object ssoCallback(@RequestParam("code") String code,
                              @RequestParam(value = "format", required = false) String format) {
        try {
            LoginResponse loginResponse = ssoService.loginByCode(code);
            if ("json".equalsIgnoreCase(format)) {
                return Result.success(loginResponse);
            }
            String token = URLEncoder.encode(loginResponse.getToken(), StandardCharsets.UTF_8.name());
            String target = ssoProperties.getFrontendRedirect();
            String sep = target.contains("?") ? "&" : "?";
            URI location = URI.create(target + sep + "token=" + token);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(location);
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            log.error("SSO 回调失败: {}", e.getMessage());
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

    /**
     * 根据邮箱查询用户信息
     *
     * @param email 用户邮箱
     * @return 用户信息
     */
    @GetMapping("/email")
    public Result<UserResponse> getUserByEmail(@RequestParam String email) {
        try {
            UserResponse userResponse = userService.getUserByEmail(email);
            return Result.success(userResponse);
        } catch (Exception e) {
            log.error("根据邮箱获取用户信息失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }


}




