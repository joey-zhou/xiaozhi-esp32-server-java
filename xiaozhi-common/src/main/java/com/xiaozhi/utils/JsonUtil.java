package com.xiaozhi.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.text.SimpleDateFormat;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtil {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static <T> String toJson(T obj) {
        String json = null;

        try {
            json = OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JsonUtil.toJson error", e);
        }

        return json;
    }

    public static <T> T fromJson(String json, Class<T> type) {
        T pojo = null;

        try {
            pojo = OBJECT_MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.error("JsonUtil.fromJson error", e);
        }
        return pojo;
    }

    public static <T> T fromJson(String json, TypeReference<T> valueTypeRef) {
        T pojo = null;

        try {
            pojo = OBJECT_MAPPER.readValue(json, valueTypeRef);
        } catch (Exception e) {
            log.error("JsonUtil.fromJson error", e);
        }
        return pojo;
    }

    static {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
//        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
