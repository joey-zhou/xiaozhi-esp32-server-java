package com.xiaozhi.repository;

import com.xiaozhi.dto.repository.SysUserWithStats;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.utils.BeanUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层 - Spring Data JPA Repository
 *
 * @author Joey
 */
@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Integer>, JpaSpecificationExecutor<SysUser> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    Optional<SysUser> findByUsername(String username);

    /**
     * 根据微信 OpenId 查询用户
     *
     * @param wxOpenId 微信 OpenId
     * @return 用户信息
     */
    Optional<SysUser> findByWxOpenId(String wxOpenId);

    /**
     * 根据微信 UnionId 查询用户
     *
     * @param wxUnionId 微信 UnionId
     * @return 用户信息
     */
    Optional<SysUser> findByWxUnionId(String wxUnionId);

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户信息
     */
    Optional<SysUser> findByEmail(String email);

    /**
     * 根据手机号查询用户
     *
     * @param tel 手机号
     * @return 用户信息
     */
    Optional<SysUser> findByTel(String tel);

    /**
     * 根据用户 ID 查询用户（包含设备数和消息数统计）
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    @Query(value = "SELECT " +
            "u.user_id, u.username, u.wx_open_id, u.wx_union_id, u.name, u.tel, u.email, u.avatar, " +
            "u.password, u.state, u.is_admin, u.role_id, u.login_ip, u.login_time, u.create_time, " +
            "(SELECT COUNT(*) FROM sys_device d WHERE d.user_id = u.user_id) AS totalDevice, " +
            "(SELECT COUNT(*) FROM sys_message m JOIN sys_device d ON d.device_id = m.device_id WHERE d.user_id = u.user_id) AS totalMessage, " +
            "(SELECT COUNT(*) FROM sys_device d WHERE d.user_id = u.user_id AND d.state = '1') AS aliveNumber " +
            "FROM sys_user u WHERE u.user_id = :userId",
            nativeQuery = true)
    Optional<SysUser> findUserWithStats(@Param("userId") Integer userId);

    /**
     * 查询用户列表（包含设备数和消息数统计）- 分页
     *
     * @param username 用户名（可选）
     * @param email    邮箱（可选）
     * @param tel      手机号（可选）
     * @param name     姓名（可选）
     * @param isAdmin  是否管理员（可选）
     * @param pageable 分页参数
     * @return 用户分页列表
     */

    @Query(value = "SELECT " +
            "u.user_id, u.username, u.wx_open_id, u.wx_union_id, u.password, u.role_id, u.name, " +
            "CASE WHEN LENGTH(u.tel) > 7 THEN CONCAT(LEFT(u.tel, 3), '****', RIGHT(u.tel, 4)) ELSE u.tel END AS tel, " +
            "u.email, u.avatar, u.state, u.is_admin, u.login_ip, u.login_time, u.create_time, " +
            "(SELECT COUNT(*) FROM sys_device d WHERE d.user_id = u.user_id) AS totalDevice, " +
            "(SELECT COUNT(*) FROM sys_message m JOIN sys_device d ON d.device_id = m.device_id WHERE d.user_id = u.user_id) AS totalMessage, " +
            "(SELECT COUNT(*) FROM sys_device d WHERE d.user_id = u.user_id AND d.state = '1') AS aliveNumber " +
            "FROM sys_user u WHERE 1=1 " +
            "AND (:username IS NULL OR :username = '') " +
            "AND (:email IS NULL OR :email = '' OR u.email = :email) " +
            "AND (:tel IS NULL OR :tel = '' OR u.tel = :tel) " +
            "AND (:name IS NULL OR :name = '' OR u.name LIKE %:name%) " +
            "AND (:isAdmin IS NULL OR :isAdmin = '' OR u.is_admin = :isAdmin) " +
            "ORDER BY u.create_time DESC",
            countQuery = "SELECT COUNT(*) " +
                    "FROM sys_user u WHERE 1=1 " +
                    "AND (:username IS NULL OR :username = '') " +
                    "AND (:email IS NULL OR :email = '' OR u.email = :email) " +
                    "AND (:tel IS NULL OR :tel = '' OR u.tel = :tel) " +
                    "AND (:name IS NULL OR :name = '' OR u.name LIKE %:name%) " +
                    "AND (:isAdmin IS NULL OR :isAdmin = '' OR u.is_admin = :isAdmin)",
            nativeQuery = true)
    Page<SysUserWithStats> findUsersWithStatsDTO(
            @Param("username") String username,
            @Param("email") String email,
            @Param("tel") String tel,
            @Param("name") String name,
            @Param("isAdmin") String isAdmin,
            Pageable pageable);

    default Page<SysUser> findUsersWithStats(
            @Param("username") String username,
            @Param("email") String email,
            @Param("tel") String tel,
            @Param("name") String name,
            @Param("isAdmin") String isAdmin,
            Pageable pageable) {
        Page<SysUserWithStats> projectionPage = findUsersWithStatsDTO(username, email, tel, name, isAdmin, pageable);
        Page<SysUser> page = BeanUtil.projectionPageToEntityPage(projectionPage, SysUser.class);

//        return projectionPage.map(this::toEntity);
        return page;
    }

    private SysUser toEntity(SysUserWithStats p) {
        SysUser user = new SysUser();
        user.setUserId(p.getUserId());
        user.setUsername(p.getUsername());
        user.setName(p.getName());
        user.setTel(p.getTel());
        user.setEmail(p.getEmail());
        user.setAvatar(p.getAvatar());
        user.setState(p.getState());
        user.setIsAdmin(p.getIsAdmin());
        user.setLoginIp(p.getLoginIp());
//        user.setLoginTime(p.getLoginTime());
//        user.setCreateTime(p.getCreateTime());
        user.setTotalDevice(p.getTotalDevice());
        user.setTotalMessage(p.getTotalMessage());
        user.setAliveNumber(p.getAliveNumber());
        return user;
    }

    /**
     * 查询验证码是否有效
     *
     * @param code  验证码
     * @param email 邮箱
     * @return 有效验证码数量
     */
    @Query(value = "SELECT COUNT(*) FROM sys_code WHERE code = :code AND email = :email AND create_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE) ORDER BY create_time DESC LIMIT 1",
            nativeQuery = true)
    Integer countValidCaptcha(@Param("code") String code, @Param("email") String email);

    // ==================== UserMapper 迁移方法 ====================

    default SysUser selectUserByUserId(Integer userId) {
        return findById(userId).orElse(null);
    }

    default SysUser selectUserByUsername(String username) {
        return findByUsername(username).orElse(null);
    }

    default SysUser selectUserByWxOpenId(String wxOpenId) {
        return findByWxOpenId(wxOpenId).orElse(null);
    }

    default SysUser selectUserByEmail(String email) {
        return findByEmail(email).orElse(null);
    }

    default SysUser selectUserByTel(String tel) {
        return findByTel(tel).orElse(null);
    }

    @Query(value = "SELECT * FROM sys_user WHERE username = :username", nativeQuery = true)
    SysUser findUserByUsername(@Param("username") String username);

    default SysUser query(@Param("username") String username, @Param("startTime") String startTime,
                          @Param("endTime") String endTime) {
        return findUserByUsername(username);
    }

    default int add(SysUser user) {
        save(user);
        return 1;
    }

    default int update(SysUser user) {
        save(user);
        return 1;
    }

    default List<SysUser> queryUsers(SysUser user) {
        return findAll();
    }

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO sys_code (email, code, create_time) VALUES (:email, LPAD(FLOOR(RAND() * 1000000), 6, '0'), NOW())", nativeQuery = true)
    int generateCode(@Param("email") String email);

    default int generateCode(SysUser user) {
        String email = user.getEmail() != null ? user.getEmail() : user.getTel();
        return generateCode(email);
    }

    default Integer queryCaptcha(@Param("code") String code, @Param("email") String email) {
        return countValidCaptcha(code, email);
    }
}
