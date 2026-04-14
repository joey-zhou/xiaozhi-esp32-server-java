package com.xiaozhi.operationlog.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.operationlog.service.OperationLogService;
import com.xiaozhi.utils.JsonUtil;
import com.xiaozhi.utils.RequestContextUtils;
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

import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
public class AuditLogAspect {

    @Resource
    private OperationLogService operationLogService;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            error = ex;
            throw ex;
        } finally {
            int costMs = (int) (System.currentTimeMillis() - start);
            saveLog(pjp, auditLog, costMs, error);
        }
    }

    private void saveLog(ProceedingJoinPoint pjp, AuditLog auditLog, int costMs, Throwable error) {
        try {
            OperationLogBO log = new OperationLogBO();
            log.setModule(auditLog.module());
            log.setOperation(auditLog.operation());
            log.setCostMs(costMs);
            log.setSuccess(error == null);

            if (error != null) {
                String msg = error.getMessage();
                log.setErrorMsg(msg != null && msg.length() > 500 ? msg.substring(0, 500) : msg);
            }

            // 用户ID
            try {
                if (StpUtil.isLogin()) {
                    Object loginId = StpUtil.getLoginId();
                    if (loginId instanceof Number n) {
                        log.setUserId(Math.toIntExact(n.longValue()));
                    } else {
                        log.setUserId(Integer.valueOf(loginId.toString().trim()));
                    }
                }
            } catch (Exception ignored) {
            }

            // 请求信息
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                log.setMethod(request.getMethod());
                log.setUrl(buildUrl(request));
                log.setIp(RequestContextUtils.getClientIp(request));
            }

            // handler 名称
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            log.setHandler(pjp.getTarget().getClass().getSimpleName() + "#" + sig.getMethod().getName());

            // 请求参数（过滤文件、response 等不可序列化的参数）
            log.setParams(serializeArgs(pjp.getArgs()));

            operationLogService.saveAsync(log);
        } catch (Exception e) {
            // 日志记录失败不影响主流程
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
        String json = JsonUtil.toJson(filtered.size() == 1 ? filtered.get(0) : filtered);
        if (json != null && json.length() > 2000) {
            json = json.substring(0, 2000) + "...(truncated)";
        }
        return json;
    }
}
