package com.riskwarning.org.service;

import com.riskwarning.common.dto.LoginResponse;

public interface SsoService {

    String buildAuthorizeUrl(String state);

    /**
     * 用授权码完成 SSO 登录，返回本系统 JWT。
     */
    LoginResponse loginByCode(String code);
}
