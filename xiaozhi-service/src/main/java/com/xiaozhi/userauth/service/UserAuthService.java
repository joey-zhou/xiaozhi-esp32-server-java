package com.xiaozhi.userauth.service;

import com.xiaozhi.common.model.bo.UserAuthBO;

public interface UserAuthService {

    UserAuthBO getByOpenIdAndPlatform(String openId, String platform);

    UserAuthBO getByUserIdAndPlatform(Integer userId, String platform);

    UserAuthBO create(UserAuthBO userAuth);

    void update(UserAuthBO userAuth);

    void deleteById(Long id);
}
