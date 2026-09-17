package com.xiaozhi.user.service;

import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.user.model.UserProjection;

public interface UserService {

    String CACHE_NAME = "XiaoZhi:User";

    PageResult<UserProjection> page(int pageNo, int pageSize, String name, String email,
                                    String tel, String isAdmin, Integer authRoleId);

    UserBO getBO(Integer userId);

    UserBO getByUsername(String username);

    UserBO getByEmail(String email);

    UserBO getByTel(String tel);

    UserBO create(UserBO user);

    void update(UserBO user);

    String generateCaptcha(String account);

    /** 校验验证码并立即消费，命中一次后同一条码不再可用；连续失败达上限会作废该账号所有未过期的码。 */
    boolean consumeCaptcha(String account, String code);

    /** 账号 state 为禁用时抛 UnauthorizedException，user 为 null 时不判断。 */
    void requireEnabled(UserBO user);
}
