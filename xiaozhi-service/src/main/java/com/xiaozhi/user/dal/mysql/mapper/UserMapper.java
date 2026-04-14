package com.xiaozhi.user.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.user.dal.mysql.dataobject.UserDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    IPage<UserResp> selectPageResp(Page<UserResp> page,
                                   @Param("name") String name,
                                   @Param("email") String email,
                                   @Param("tel") String tel,
                                   @Param("isAdmin") String isAdmin,
                                   @Param("authRoleId") Integer authRoleId);

    @Insert("INSERT INTO sys_code(email, code, createTime) VALUES(#{account}, #{code}, NOW())")
    int insertCode(@Param("account") String account, @Param("code") String code);

    @Select("""
        SELECT COUNT(*)
        FROM sys_code
        WHERE code = #{code}
          AND email = #{account}
          AND createTime >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)
        """)
    Integer countValidCode(@Param("account") String account, @Param("code") String code);
}
