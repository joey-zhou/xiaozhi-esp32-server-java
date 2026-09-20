package com.xiaozhi.operationlog.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.Sensitive;
import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.common.web.TrustedProxyPolicy;
import com.xiaozhi.operationlog.service.OperationLogService;
import com.xiaozhi.utils.DateUtils;
import com.xiaozhi.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    static final String MASK = "***";

    private static final JsonSerializer<Object> MASK_SERIALIZER = new JsonSerializer<>() {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(MASK);
        }
    };

    /** 打码模块只能挂在副本上，不能挂到 {@link JsonUtil#OBJECT_MAPPER}。 */
    private static final ObjectMapper AUDIT_MAPPER = createAuditMapper();

    @Resource
    private OperationLogService operationLogService;

    @Resource
    private TrustedProxyPolicy trustedProxyPolicy;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.nanoTime();
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            error = ex;
            throw ex;
        } finally {
            int costMs = (int) DateUtils.elapsedMillis(start);
            saveLog(pjp, auditLog, costMs, error);
        }
    }

    private void saveLog(ProceedingJoinPoint pjp, AuditLog auditLog, int costMs, Throwable error) {
        try {
            OperationLogBO entry = new OperationLogBO();
            entry.setModule(auditLog.module());
            entry.setOperation(auditLog.operation());
            entry.setCostMs(costMs);
            entry.setSuccess(error == null);

            if (error != null) {
                String msg = error.getMessage();
                entry.setErrorMsg(msg != null && msg.length() > 500 ? msg.substring(0, 500) : msg);
            }

            // 用户ID
            try {
                if (StpUtil.isLogin()) {
                    Object loginId = StpUtil.getLoginId();
                    if (loginId instanceof Number n) {
                        entry.setUserId(Math.toIntExact(n.longValue()));
                    } else {
                        entry.setUserId(Integer.valueOf(loginId.toString().trim()));
                    }
                }
            } catch (Exception e) {
                log.debug("审计日志获取当前登录用户ID失败", e);
            }

            // 请求信息
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                entry.setMethod(request.getMethod());
                entry.setUrl(buildUrl(request));
                // 审计的来源 IP 不能由请求头决定，否则审计表可被投毒
                entry.setIp(trustedProxyPolicy.resolveClientIp(request));
            }

            // handler 名称
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            entry.setHandler(pjp.getTarget().getClass().getSimpleName() + "#" + sig.getMethod().getName());

            // 请求参数（过滤文件、response 等不可序列化的参数）
            entry.setParams(serializeArgs(pjp.getArgs()));

            operationLogService.saveAsync(entry);
        } catch (Exception e) {
            log.warn("审计日志保存失败", e);
        }
    }

    private String buildUrl(HttpServletRequest request) {
        String qs = request.getQueryString();
        if (qs == null || qs.isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + qs;
    }

    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> filtered = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile
                    || arg instanceof MultipartFile[]) {
                continue;
            }
            filtered.add(arg);
        }
        if (filtered.isEmpty()) {
            return null;
        }
        String json = writeMasked(filtered.size() == 1 ? filtered.get(0) : filtered);
        if (json != null && json.length() > 2000) {
            json = json.substring(0, 2000) + "...(truncated)";
        }
        return json;
    }

    /** 序列化失败必须返回 null，不能回退到未打码序列化。 */
    private static String writeMasked(Object value) {
        try {
            return AUDIT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static ObjectMapper createAuditMapper() {
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                    BeanDescription beanDesc, List<BeanPropertyWriter> properties) {
                Set<String> masked = new HashSet<>();
                for (BeanPropertyDefinition property : beanDesc.findProperties()) {
                    // Sensitive 注解只打在字段上，必须查字段而非 getter
                    if (property.hasField() && property.getField().getAnnotation(Sensitive.class) != null) {
                        masked.add(property.getName());
                    }
                }
                for (BeanPropertyWriter writer : properties) {
                    if (masked.contains(writer.getName())) {
                        writer.assignSerializer(MASK_SERIALIZER);
                    }
                }
                return properties;
            }
        });
        return JsonUtil.OBJECT_MAPPER.copy().registerModule(module);
    }

}
