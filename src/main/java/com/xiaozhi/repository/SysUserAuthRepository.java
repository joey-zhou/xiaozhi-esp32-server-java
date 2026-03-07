package com.xiaozhi.repository;

import com.xiaozhi.entity.SysUserAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户第三方认证 Repository 接口
 *
 * @author Joey
 */
@Repository
public interface SysUserAuthRepository extends JpaRepository<SysUserAuth, Long>, JpaSpecificationExecutor<SysUserAuth> {

    /**
     * 根据 openId 和平台查询认证信息
     */
    @Query(value = "SELECT * FROM sys_user_auth WHERE openId = :openId AND platform = :platform", nativeQuery = true)
    SysUserAuth selectByOpenIdAndPlatform(@Param("openId") String openId, @Param("platform") String platform);

    /**
     * 根据用户 ID 和平台查询认证信息
     */
    @Query(value = "SELECT * FROM sys_user_auth WHERE userId = :userId AND platform = :platform", nativeQuery = true)
    SysUserAuth selectByUserIdAndPlatform(@Param("userId") Integer userId, @Param("platform") String platform);

    /**
     * 根据 openId 和平台查询认证信息
     */
    @Query(value = "SELECT * FROM sys_user_auth WHERE openId = :openId AND platform = :platform", nativeQuery = true)
    SysUserAuth findByOpenIdAndPlatform(@Param("openId") String openId, @Param("platform") String platform);

    /**
     * 根据用户 ID 和平台查询认证信息
     */
    @Query(value = "SELECT * FROM sys_user_auth WHERE userId = :userId AND platform = :platform", nativeQuery = true)
    SysUserAuth findByUserIdAndPlatform(@Param("userId") Integer userId, @Param("platform") String platform);

    /**
     * 根据用户 ID 查询认证信息
     */
    @Query(value = "SELECT * FROM sys_user_auth WHERE userId = :userId", nativeQuery = true)
    List<SysUserAuth> findByUserId(@Param("userId") Integer userId);

    default int insert(SysUserAuth userAuth) {
        save(userAuth);
        return 1;
    }

    default int update(SysUserAuth userAuth) {
        save(userAuth);
        return 1;
    }

    @Modifying
    @Query(value = "DELETE FROM sys_user_auth WHERE id = :id", nativeQuery = true)
    int deleteByAuthId(@Param("id") Long id);

    default int deleteAuthById(@Param("id") Long id) {
        return deleteByAuthId(id);
    }
}
