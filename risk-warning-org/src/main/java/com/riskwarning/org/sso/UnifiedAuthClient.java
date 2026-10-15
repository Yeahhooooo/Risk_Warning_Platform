package com.riskwarning.org.sso;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.org.config.SsoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 统一身份认证平台客户端（授权码换令牌 + 用户信息 SM2 解密）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnifiedAuthClient {

    private final SsoProperties ssoProperties;

    public String buildAuthorizeUrl(String state) {
        String url = ssoProperties.getBaseUrl() + "/ssoapi/oauth/authorize"
                + "?response_type=code"
                + "&client_id=" + urlEncode(ssoProperties.getClientId())
                + "&client_secret=" + urlEncode(ssoProperties.getClientSecret())
                + "&redirect_uri=" + urlEncode(ssoProperties.getRedirectUri());
        if (state != null && !state.isEmpty()) {
            url += "&state=" + urlEncode(state);
        }
        return url;
    }

    public TokenResult exchangeToken(String code) throws Exception {
        String form = "code=" + urlEncode(code)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + urlEncode(ssoProperties.getRedirectUri());

        String basic = Base64.getEncoder().encodeToString(
                (ssoProperties.getClientId() + ":" + ssoProperties.getClientSecret())
                        .getBytes(StandardCharsets.UTF_8));

        String body = httpPost(ssoProperties.getBaseUrl() + "/ssoapi/oauth/token",
                "application/x-www-form-urlencoded",
                form.getBytes(StandardCharsets.UTF_8),
                "Basic " + basic);

        JSONObject root = JSON.parseObject(body);
        if (root == null || root.getLongValue("code") != 0L) {
            throw new IllegalStateException("换取令牌失败：" + body);
        }
        JSONObject obj = root.getJSONObject("obj");
        if (obj == null) {
            throw new IllegalStateException("换取令牌失败，缺少 obj：" + body);
        }

        TokenResult result = new TokenResult();
        result.setAccessToken(obj.getString("accessToken"));
        result.setExpiresIn(obj.getLongValue("expiresIn"));
        result.setRefreshToken(obj.getString("refreshToken"));
        return result;
    }

    public SsoUserInfo getUserInfo(String accessToken) throws Exception {
        String methodName = "userInformation";
        String time = String.valueOf(System.currentTimeMillis());
        String sign = md5Hex(ssoProperties.getClientId() + methodName + time);

        String url = ssoProperties.getBaseUrl() + "/serviceapi/interface.do"
                + "?client_id=" + urlEncode(ssoProperties.getClientId())
                + "&time=" + time
                + "&sign=" + sign
                + "&methodName=" + methodName;

        String body = httpGet(url, "Bearer " + accessToken);
        JSONObject root = JSON.parseObject(body);
        if (root == null || root.getLongValue("code") != 0L) {
            throw new IllegalStateException("获取用户信息失败：" + body);
        }

        String cipherHex = root.getString("obj");
        String plain = sm2Decrypt(cipherHex);
        JSONObject userJson = JSON.parseObject(plain);
        if (userJson == null) {
            throw new IllegalStateException("用户信息解密后不是合法 JSON");
        }

        SsoUserInfo user = new SsoUserInfo();
        user.setUsername(userJson.getString("username"));
        user.setRealname(userJson.getString("realname"));
        user.setMobile(userJson.getString("mobile"));
        user.setCertificationId(userJson.getString("certificationId"));
        user.setEnterpriseName(userJson.getString("enterpriseName"));
        user.setCreditCode(userJson.getString("creditCode"));
        user.setLegalPersonName(userJson.getString("legalPersonName"));
        user.setCertificationStatus(userJson.getString("certificationStatus"));
        return user;
    }

    private String sm2Decrypt(String cipherHex) throws Exception {
        X9ECParameters x9 = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domain = new ECDomainParameters(
                x9.getCurve(), x9.getG(), x9.getN(), x9.getH());

        BigInteger d = new BigInteger(ssoProperties.getAppword().replaceAll("\\s", ""), 16);
        ECPrivateKeyParameters privateKey = new ECPrivateKeyParameters(d, domain);

        byte[] cipher = Hex.decode(cipherHex.replaceAll("\\s", ""));
        int c1Len = 1 + 2 * ((x9.getCurve().getFieldSize() + 7) / 8);
        if (cipher.length > c1Len && cipher[0] != 0x04) {
            byte[] withPrefix = new byte[cipher.length + 1];
            withPrefix[0] = 0x04;
            System.arraycopy(cipher, 0, withPrefix, 1, cipher.length);
            cipher = withPrefix;
        }

        SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
        engine.init(false, privateKey);
        return new String(engine.processBlock(cipher, 0, cipher.length), StandardCharsets.UTF_8);
    }

    private static String httpGet(String url, String authorization) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Authorization", authorization);
        return readBody(conn);
    }

    private static String httpPost(String url, String contentType, byte[] body, String authorization)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("Authorization", authorization);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        return readBody(conn);
    }

    private static String readBody(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (in != null) {
            byte[] chunk = new byte[4096];
            int len;
            while ((len = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, len);
            }
            in.close();
        }
        conn.disconnect();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String md5Hex(String text) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @lombok.Data
    public static class TokenResult {
        private String accessToken;
        private long expiresIn;
        private String refreshToken;
    }

    @lombok.Data
    public static class SsoUserInfo {
        private String username;
        private String realname;
        private String mobile;
        private String certificationId;
        private String enterpriseName;
        private String creditCode;
        private String legalPersonName;
        private String certificationStatus;
    }
}
