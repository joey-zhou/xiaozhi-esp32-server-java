package com.xiaozhi.user.service;

import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.UserResp;

public interface UserService {

    String CACHE_NAME = "XiaoZhi:User";

    PageResp<UserResp> page(int pageNo, int pageSize, String name, String email,
                            String tel, String isAdmin, Integer authRoleId);

    UserBO getBO(Integer userId);

    UserBO getByUsername(String username);

    UserBO getByEmail(String email);

    UserBO getByTel(String tel);

    UserBO create(UserBO user);

    void update(UserBO user);

    String generateCaptcha(String account);

    boolean checkCaptcha(String account, String code);
}
