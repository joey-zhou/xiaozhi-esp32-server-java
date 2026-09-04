package com.xiaozhi.user.convert;

import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.user.model.UserProjection;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 投影按名映射到 Resp，列别名与 Resp 字段名错一个字就静默为 null；
 * SQL 里已脱敏的 tel 必须原样透传。
 */
class UserConvertTest {

    private final UserConvert convert = new UserConvertImpl();

    @Test
    void toRespFromProjectionCarriesEveryColumn() {
        UserProjection projection = new UserProjection();
        projection.setUserId(7);
        projection.setUsername("alice");
        projection.setName("Alice");
        projection.setTel("138****1234");
        projection.setEmail("alice@example.com");
        projection.setAvatar("avatar/alice.png");
        projection.setState("1");
        projection.setIsAdmin("0");
        projection.setAuthRoleId(2);
        projection.setAuthRoleName("普通用户");
        projection.setLoginIp("127.0.0.1");
        projection.setLoginTime(LocalDateTime.of(2026, 9, 4, 10, 0));
        projection.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        projection.setUpdateTime(LocalDateTime.of(2026, 9, 1, 0, 0));
        projection.setTotalDevice(3);
        projection.setTotalMessage(42);
        projection.setAliveNumber(1);

        UserResp resp = convert.toResp(projection);

        assertThat(resp).usingRecursiveComparison().isEqualTo(projection);
        assertThat(resp.getTel()).isEqualTo("138****1234");
    }

    @Test
    void toRespFromBOLeavesAggregateColumnsNull() {
        UserBO bo = new UserBO();
        bo.setUserId(7);
        bo.setUsername("alice");
        bo.setPassword("encoded");
        bo.setTel("13800001234");

        UserResp resp = convert.toResp(bo);

        assertThat(resp.getUserId()).isEqualTo(7);
        assertThat(resp.getTel()).isEqualTo("13800001234");
        assertThat(resp.getAuthRoleName()).isNull();
        assertThat(resp.getTotalDevice()).isNull();
        assertThat(resp.getTotalMessage()).isNull();
        assertThat(resp.getAliveNumber()).isNull();
    }
}
