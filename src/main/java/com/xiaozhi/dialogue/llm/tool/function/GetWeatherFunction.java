package com.xiaozhi.dialogue.llm.tool.function;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.HttpUtil;
import com.xiaozhi.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class GetWeatherFunction implements ToolsGlobalRegistry.GlobalFunction {


    @Override
    public ToolCallback getFunctionCallTool(ChatSession chatSession) {
        return toolCallback;
    }

    ToolCallback toolCallback = FunctionToolCallback
            .builder("func_getWeather", (Map<String, String> params, ToolContext toolContext) -> {
                ChatSession chatSession = (ChatSession)toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                String location = params.get("location");
                log.info("从聊天记录中获取的地址参数{}",location);
                String result = fetchCityInfo(location);
                return result;
            })
            .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
            .description("Get the current weather by the user's prompt, \" +\n" +
                    "            \"before call the function ,please help me check if user prompt contains location address, if not set empty string to user prompt")
            .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "location": {
                                    "type": "string",
                                    "description": "需要查询的地址信息（城市或者地名）"
                                }
                            },
                            "required": ["location"]
                        }
                    """)
            .inputType(Map.class)
            .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
            .build();


    public static JSONObject parseWeatherInfo(String url){
        JSONObject result = new JSONObject();
        // 发送 GET 请求
        Document document = null;
        try {
            document = Jsoup.connect(url).get();
            Elements h1Elements = document.select("h1.c-submenu__location");
            Elements currentAbstractElements = document.select(".c-city-weather-current .current-abstract");
            String cityName = h1Elements.text();
            String currentAbstract = currentAbstractElements.text();

            Elements currentBasicElements = document.select(".c-city-weather-current .current-basic .current-basic___item");
            result.put("cityName", cityName);
            System.out.println(currentAbstract);
            result.put("currentAbstract", currentAbstract);
            System.out.println(currentBasicElements);
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //System.out.println(document.title());
    }

    private final OkHttpClient client = HttpUtil.client;

    public String fetchCityInfo(String location) {
        if (StringUtils.isBlank(location)){
            location = getLocationByIP(CmsUtils.getLocalIpAddress());
        }
        log.info("最终的地址是 {}",location);
        String token="eyJhbGciOiAiRWREU0EiLCAia2lkIjogIkNHNUE2Q1JWOUMifQ==.eyJzdWIiOiAiM0YyREdIOE02UiIsICJpYXQiOiAxNzU4ODgyMTc2LCAiZXhwIjogMTc1ODg4MzA3Nn0=.ThxuTSURFUooVnrmpgdi8RKf7ZUZ4w4vJn4HuEfuP9ahCT2PQ3d0QkQk18W2kCWnmEV7MJYTFph33qAEdx59AA==";
        String host = "kv6vhf8g58.re.qweatherapi.com";

        String url = "https://"+host+"/geo/v2/city/lookup?location="+location;
        var request = new Request.Builder()
                .url(url)
                .addHeader("Authorization","Bearer "+token)
                .addHeader("Content-Type", "application/json")
                .get()
                .build();
        try (Response resp = client.newCall(request).execute()) {
            if (resp.isSuccessful()) {
                JSONObject jsonObject = JsonUtil.fromJson(resp.body().string(), JSONObject.class);
                log.info(jsonObject.toString());
                JSONArray jsonArray = (JSONArray) jsonObject.getJSONArray("location");
                JSONObject locationObject = (JSONObject)jsonArray.getJSONObject(0);
                String fxLink = (String)locationObject.get("fxLink");
                //HttpResponse fxLinkResponse  = HttpRequest.get(fxLink).execute();
                //System.out.println(fxLinkResponse.body());
                JSONObject result = parseWeatherInfo(fxLink);
                log.info(result.toString());
                return result.getString("currentAbstract");
            } else {
                log.error("查询天气信息失败 {}", resp.body().string());
                return "查询天气信息失败";
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) {
        GetWeatherFunction getWeatherFunction = new GetWeatherFunction();
        getWeatherFunction.fetchCityInfo("上海");


    }

    public void createToken()throws Exception{
        // Private key
        String privateKeyString = "-----BEGIN PRIVATE KEY-----\n" +
                "MC4CAQAwBQYDK2VwBCIEIOqloJ6PrTgH/OGNiHlFcP3RmvoHoYq7F3soAFaRuXu/\n" +
                "-----END PRIVATE KEY-----\n";
        privateKeyString = privateKeyString.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").trim();
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyString);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EdDSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

// Header
        String headerJson = "{\"alg\": \"EdDSA\", \"kid\": \"CG5A6CRV9C\"}";

// Payload
        long iat = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond() - 30;
        long exp = iat + 900;
        String payloadJson = "{\"sub\": \"3F2DGH8M6R\", \"iat\": " + iat + ", \"exp\": " + exp + "}";

// Base64url header+payload
        String headerEncoded = Base64.getUrlEncoder().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadEncoded = Base64.getUrlEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String data = headerEncoded + "." + payloadEncoded;

// Sign
        Signature signer = Signature.getInstance("EdDSA");
        signer.initSign(privateKey);
        signer.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] signature = signer.sign();

        String signatureEncoded = Base64.getUrlEncoder().encodeToString(signature);

        String jwt = data + "." + signatureEncoded;

// Print Token
        System.out.println("Signature:\n" + signatureEncoded);
        System.out.println("JWT:\n" + jwt);
    }


    public static String getLocationByIP(String ip) {
        log.info("ip= {}",ip);
        if (ip == null || ip.length() == 0 || "127.0.0.1".equalsIgnoreCase(ip)){
            return "上海";
        }
        String apiUrl = "http://ip-api.com/json/" + ip + "?lang=zh-CN"; // 中文地址
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String inputLine;
            StringBuilder content = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }

            in.close();
            conn.disconnect();
            JSONObject json = JSONObject.parseObject(content.toString());
            if ("success".equals(json.getString("status"))) {
                return json.getString("country") + " " + json.getString("regionName") + " " + json.getString("city");
            } else {
                return "地址获取失败：" + json.getString("message");
            }

        } catch (Exception e) {
            return "调用IP地址解析失败：" + e.getMessage();
        }
    }





}
