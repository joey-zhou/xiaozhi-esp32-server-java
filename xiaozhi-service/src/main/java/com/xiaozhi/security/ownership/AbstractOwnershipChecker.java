package com.xiaozhi.security.ownership;

import com.xiaozhi.common.exception.UnauthorizedException;
import org.springframework.util.StringUtils;

import java.util.Objects;

public abstract class AbstractOwnershipChecker implements OwnershipChecker {

    private final String resource;

    public AbstractOwnershipChecker(String resource) {
        this.resource = resource;
    }

    @Override
    public String getResource() {
        return resource;
    }

    protected final void requireOwner(Integer ownerId, Integer userId, String message) {
        if (!Objects.equals(ownerId, userId)) {
            throw new UnauthorizedException(message);
        }
    }

    protected final Integer toIntId(Object value, String fieldName) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Integer.valueOf(text.trim());
        }
        throw new IllegalArgumentException(fieldName + " 参数类型不合法");
    }

    protected final Long toLongId(Object value, String fieldName) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.valueOf(text.trim());
        }
        throw new IllegalArgumentException(fieldName + " 参数类型不合法");
    }

    protected final String toStrId(Object value, String fieldName) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw new IllegalArgumentException(fieldName + " 参数类型不合法");
    }
}
