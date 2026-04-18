package com.xiaozhi.token.provider;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.token.TokenCache;
import com.xiaozhi.utils.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AliyunTokenProvider implements TokenProvider {

    private static final List<String> SUPPORTED_PROVIDERS = List.of("aliyun", "aliyun-nls");

    private static final String REGION_ID = "cn-shanghai";
    private static final String DOMAIN = "nls-meta.cn-shanghai.aliyuncs.com";
    private static final String API_VERSION = "2019-02-28";
    private static final String REQUEST_ACTION = "CreateToken";

    @Override
    public List<String> getSupportedProviders() {
        return SUPPORTED_PROVIDERS;
    }

    @Override
    public TokenCache fetchToken(ConfigBO config) {
        try {
            DefaultProfile profile = DefaultProfile.getProfile(REGION_ID, config.getAk(), config.getSk());
            IAcsClient client = new DefaultAcsClient(profile);
            CommonRequest request = new CommonRequest();
            request.setDomain(DOMAIN);
            request.setVersion(API_VERSION);
            request.setAction(REQUEST_ACTION);
            request.setMethod(MethodType.POST);
            request.setProtocol(ProtocolType.HTTPS);

            CommonResponse response = client.getCommonResponse(request);
            if (response.getHttpStatus() != 200) {
                throw new IllegalStateException("阿里云API返回错误，HTTP状态码: " + response.getHttpStatus()
                        + ", 响应: " + response.getData());
            }

            JsonNode root = JsonUtil.OBJECT_MAPPER.readTree(response.getData());
            JsonNode tokenNode = root.path("Token");
            String token = tokenNode.path("Id").asText(null);
            long expireTimeSeconds = tokenNode.path("ExpireTime").asLong(0L);
            if (token == null || expireTimeSeconds <= 0L) {
                throw new IllegalStateException("阿里云Token响应不完整: " + response.getData());
            }
            return new TokenCache(token, expireTimeSeconds * 1000L);
        } catch (ClientException e) {
            log.error("调用阿里云API失败: {}", e.getMessage(), e);
            throw new IllegalStateException("调用阿里云API失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("获取阿里云Token失败: " + e.getMessage(), e);
        }
    }
}
