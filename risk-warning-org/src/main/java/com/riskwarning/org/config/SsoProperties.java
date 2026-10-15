package com.riskwarning.org.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sso")
public class SsoProperties {

    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private String appword;
    private String redirectUri;
    private String frontendRedirect;
}
