package com.xiaozhi.token.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.token.TokenCache;
import com.xiaozhi.utils.JsonUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CozeTokenProvider implements TokenProvider {

    private static final List<String> SUPPORTED_PROVIDERS = List.of("coze");

    private static final String COZE_API_ENDPOINT = "api.coze.cn";
    private static final String TOKEN_URL = "https://api.coze.cn/api/permission/oauth2/token";
    private static final int JWT_EXPIRE_SECONDS = 600;
    private static final int DEFAULT_DURATION_SECONDS = 86399;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<String> getSupportedProviders() {
        return SUPPORTED_PROVIDERS;
    }

    @Override
    public TokenCache fetchToken(ConfigBO config) {
        try {
            String jwt = generateJwt(config);
            return requestAccessToken(jwt);
        } catch (Exception e) {
            throw new IllegalStateException("刷新Coze Token失败: " + e.getMessage(), e);
        }
    }

    private String generateJwt(ConfigBO config) throws Exception {
        long currentTime = System.currentTimeMillis() / 1000;
        Map<String, Object> header = Map.of(
                "alg", "RS256",
                "typ", "JWT",
                "kid", config.getAk()
        );
        Map<String, Object> claims = Map.of(
                "iss", config.getAppId(),
                "aud", COZE_API_ENDPOINT,
                "iat", currentTime,
                "exp", currentTime + JWT_EXPIRE_SECONDS,
                "jti", UUID.randomUUID().toString()
        );

        String headerJson = JsonUtil.toJson(header);
        String claimsJson = JsonUtil.toJson(claims);
        if (headerJson == null || claimsJson == null) {
            throw new IllegalStateException("生成Coze JWT失败");
        }

        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedClaims = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
        String content = encodedHeader + "." + encodedClaims;

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(parsePrivateKey(config.getSk()));
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        return content + "." + encodedSignature;
    }

    private PrivateKey parsePrivateKey(String privateKeyStr) throws Exception {
        String cleanKey = privateKeyStr
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleanKey);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    private TokenCache requestAccessToken(String jwt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt);
        Map<String, Object> requestBody = Map.of(
                "duration_seconds", DEFAULT_DURATION_SECONDS,
                "grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_URL, request, String.class);
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new IllegalStateException("Coze API返回错误，HTTP状态码: " + response.getStatusCode()
                    + ", 响应: " + response.getBody());
        }

        JsonNode jsonResponse = JsonUtil.OBJECT_MAPPER.readTree(response.getBody());
        String accessToken = jsonResponse.path("access_token").asText(null);
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalStateException("响应中未找到access_token字段");
        }

        long expiresIn = jsonResponse.path("expires_in").asLong(DEFAULT_DURATION_SECONDS);
        long expireAt = System.currentTimeMillis() + expiresIn * 1000L;
        return new TokenCache(accessToken, expireAt);
    }
}
