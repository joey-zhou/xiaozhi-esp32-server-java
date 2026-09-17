package com.xiaozhi.user.service;

import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.user.model.UserProjection;

public interface UserService {

    PageResult<UserProjection> page(int pageNo, int pageSize, String name, String email,
                                    String tel, String isAdmin, Integer authRoleId);

    UserBO getBO(Integer userId);

    UserBO getByUsername(String username);

    UserBO getByEmail(String email);

    UserBO getByTel(String tel);

    UserBO create(UserBO user);

    /**
     * 局部更新：user 里为 null 的字段保持库里原值。
     *
     * @return 落库后的完整用户信息，调用方不必再查一遍
     */
    UserBO update(UserBO user);

    /** 账号 state 为禁用时抛 UnauthorizedException，user 为 null 时不判断。 */
    void requireEnabled(UserBO user);
}
