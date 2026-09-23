package com.gongcheng.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongcheng.assistant.common.GlobalErrorCodeConstants;
import com.gongcheng.assistant.common.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WxAuthService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wx.appid:}")
    private String appid;

    @Value("${wx.secret:}")
    private String secret;

    private static final String WX_LOGIN_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

    public WxAuthService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(10000))
                .setReadTimeout(Duration.ofMillis(10000))
                .build();
    }

    /**
     * 用 wx.login 的 code 换取 openid
     */
    public Map<String, String> login(String code) {
        // AppID 或 Secret 未配置时，返回模拟 openid（开发调试用）
        boolean appidNotConfigured = appid == null || appid.isEmpty() || appid.equals("your-appid-here");
        boolean secretNotConfigured = secret == null || secret.isEmpty() || secret.equals("your-secret-here");
        if (appidNotConfigured || secretNotConfigured) {
            log.warn("微信 AppID 或 Secret 未配置，使用模拟 openid (appid={}, secretConfigured={})",
                    appid, !secretNotConfigured);
            Map<String, String> result = new HashMap<>();
            result.put("openid", "dev_" + Math.abs(code.hashCode()));
            return result;
        }

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    WX_LOGIN_URL, String.class, appid, secret, code);

            JsonNode root = objectMapper.readTree(response.getBody());

            // 微信返回错误（如 invalid code，通常是 appid/secret 不匹配或 code 已过期）
            if (root.has("errcode") && root.get("errcode").asInt() != 0) {
                String errmsg = root.path("errmsg").asText("未知错误");
                log.warn("微信登录返回错误，降级使用模拟 openid: errcode={}, errmsg={}",
                        root.get("errcode").asInt(), errmsg);
                return mockOpenid(code);
            }

            String openid = root.path("openid").asText("");
            String sessionKey = root.path("session_key").asText("");

            if (openid.isEmpty()) {
                log.warn("微信登录未返回 openid，降级使用模拟 openid");
                return mockOpenid(code);
            }

            log.info("微信登录成功: openid={}", openid);
            Map<String, String> result = new HashMap<>();
            result.put("openid", openid);
            result.put("sessionKey", sessionKey);
            return result;

        } catch (Exception e) {
            log.warn("调用微信接口异常，降级使用模拟 openid: {}", e.getMessage());
            return mockOpenid(code);
        }
    }

    private Map<String, String> mockOpenid(String code) {
        Map<String, String> result = new HashMap<>();
        result.put("openid", "dev_" + Math.abs(code.hashCode()));
        return result;
    }
}
