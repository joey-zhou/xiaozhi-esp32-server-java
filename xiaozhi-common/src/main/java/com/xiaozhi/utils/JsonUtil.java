package com.xiaozhi.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;

public class JsonUtil {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static <T> String toJson(T obj) {
        String json = null;

        try {
            json = OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            logger.error("JsonUtil.toJson error", e);
        }

        return json;
    }

    public static <T> T fromJson(String json, Class<T> type) {
        T pojo = null;

        try {
            pojo = OBJECT_MAPPER.readValue(json, type);
        } catch (Exception e) {
            logger.error("JsonUtil.fromJson error", e);
        }
        return pojo;
    }

    public static <T> T fromJson(String json, TypeReference<T> valueTypeRef) {
        T pojo = null;

        try {
            pojo = OBJECT_MAPPER.readValue(json, valueTypeRef);
        } catch (Exception e) {
            logger.error("JsonUtil.fromJson error", e);
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
