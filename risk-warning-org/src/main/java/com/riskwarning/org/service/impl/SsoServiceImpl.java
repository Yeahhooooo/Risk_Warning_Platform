package com.riskwarning.org.service.impl;

import com.riskwarning.common.dto.LoginResponse;
import com.riskwarning.common.dto.UserResponse;
import com.riskwarning.common.po.user.User;
import com.riskwarning.common.utils.JwtUtils;
import com.riskwarning.common.utils.PasswordUtils;
import com.riskwarning.org.repository.UserRepository;
import com.riskwarning.org.service.SsoService;
import com.riskwarning.org.sso.UnifiedAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SsoServiceImpl implements SsoService {

    private static final String SSO_EMAIL_SUFFIX = "@sso.local";

    private final UnifiedAuthClient unifiedAuthClient;
    private final UserRepository userRepository;
    private final PasswordUtils passwordUtils;
    private final JwtUtils jwtUtils;

    @Override
    public String buildAuthorizeUrl(String state) {
        return unifiedAuthClient.buildAuthorizeUrl(state);
    }

    @Override
    @Transactional
    public LoginResponse loginByCode(String code) {
        try {
            UnifiedAuthClient.TokenResult tokenResult = unifiedAuthClient.exchangeToken(code);
            UnifiedAuthClient.SsoUserInfo ssoUser = unifiedAuthClient.getUserInfo(tokenResult.getAccessToken());

            if (!StringUtils.hasText(ssoUser.getUsername())) {
                throw new RuntimeException("SSO 用户信息缺少 username");
            }

            User user = findOrCreateLocalUser(ssoUser);
            String token = jwtUtils.generateToken(user.getId(), user.getEmail(), user.getFullName());

            UserResponse userResponse = new UserResponse();
            BeanUtils.copyProperties(user, userResponse);
            return new LoginResponse(token, userResponse);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("SSO 登录失败", e);
            throw new RuntimeException("SSO 登录失败: " + e.getMessage(), e);
        }
    }

    private User findOrCreateLocalUser(UnifiedAuthClient.SsoUserInfo ssoUser) {
        String email = ssoUser.getUsername().trim() + SSO_EMAIL_SUFFIX;
        return userRepository.findByEmail(email).map(existing -> {
            boolean changed = false;
            if (StringUtils.hasText(ssoUser.getRealname())
                    && !ssoUser.getRealname().equals(existing.getFullName())) {
                existing.setFullName(ssoUser.getRealname());
                changed = true;
            }
            if (changed) {
                existing.setUpdatedAt(LocalDateTime.now());
                return userRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setFullName(StringUtils.hasText(ssoUser.getRealname())
                    ? ssoUser.getRealname()
                    : ssoUser.getUsername());
            // SSO 用户不走密码登录，占位即可
            user.setPassword(passwordUtils.encodePassword(UUID.randomUUID().toString()));
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            log.info("SSO 自动创建本地用户: username={}, email={}", ssoUser.getUsername(), email);
            return userRepository.save(user);
        });
    }
}
