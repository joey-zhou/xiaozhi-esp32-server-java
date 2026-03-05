package com.xiaozhi.repository;

import com.xiaozhi.entity.SysCode;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.utils.BeanUtil;
import com.xiaozhi.utils.SpringContextUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 验证码 Repository 接口
 * @author Joey
 */
@Repository
public interface SysCodeRepository extends JpaRepository<SysCode, Integer>, JpaSpecificationExecutor<SysCode> {

    /**
     * 查询验证码（Spring Data JPA 方式）
     * 对应 MyBatis XML 中的 queryVerifyCode 查询
     *
     * @param deviceId 设备 ID（可选）
     * @param sessionId 会话 ID（可选）
     * @param code 验证码（可选）
     * @param createTime 起始时间（可选，为 null 时默认查询最近 10 分钟）
     * @return 最新的验证码记录
     */
    default SysCode queryVerifyCode(String deviceId, String sessionId, String code, Date createTime) {
        // ORDER BY createTime DESC LIMIT 1
        var pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createTime"));

         SysCodeRepository sysCodeRepository = SpringContextUtil.ac.getBean(SysCodeRepository.class);
        List<SysCode> result =  sysCodeRepository.findAll((root, query, cb) -> {
            var predicates = cb.conjunction();

            // 动态条件：deviceId != null and deviceId != ''
            if (deviceId != null && !deviceId.isEmpty()) {
                predicates = cb.and(predicates, cb.equal(root.get("deviceId"), deviceId));
            }

            // 动态条件：sessionId != null and sessionId != ''
            if (sessionId != null && !sessionId.isEmpty()) {
                predicates = cb.and(predicates, cb.equal(root.get("sessionId"), sessionId));
            }

            // 动态条件：code != null and code != ''
            if (code != null && !code.isEmpty()) {
                predicates = cb.and(predicates, cb.equal(root.get("code"), code));
            }

            // 时间条件：createTime != null ? createTime >= #{createTime} : createTime >= DATE_SUB(NOW(),INTERVAL 10 MINUTE)
            Date queryTime = createTime != null ? createTime : getTenMinutesAgo();
            predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("createTime"), queryTime));

            return predicates;
        },Sort.by(Sort.Direction.DESC, "createTime"));


        return result. size()>0 ? result.get(0) : null;
    }

    /**
     * 获取 10 分钟前的时间
     */
    default Date getTenMinutesAgo() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -10);
        return calendar.getTime();
    }

    /**
     * 根据 ID 查询验证码
     */
    @Query(value = "SELECT * FROM sys_code WHERE code_id = :codeId", nativeQuery = true)
    SysCode selectByCodeId(@Param("codeId") Integer codeId);

    /**
     * 生成验证码
     */
    @Modifying
    @Query(value = "INSERT INTO sys_code (device_id, session_id, type, code, create_time) " +
                   "VALUES (:deviceId, :sessionId, :type, LPAD(FLOOR(RAND() * 1000000), 6, '0'), NOW())", nativeQuery = true)
    int generateCode(@Param("sysCode") SysCode sysCode);
    default int generateCode(SysDevice sysDevice){
        return generateCode(BeanUtil.copyProperties(sysDevice,new SysCode()));
    }

    /**
     * 根据邮箱和类型查询
     */
    @Query(value = "SELECT * FROM sys_code WHERE email = :email AND type = :type ORDER BY create_time DESC LIMIT 1", nativeQuery = true)
    SysCode selectByEmailAndType(@Param("email") String email, @Param("type") String type);

    /**
     * 删除验证码
     */
    @Modifying
    @Query(value = "DELETE FROM sys_code WHERE code_id = :codeId", nativeQuery = true)
    int delete(@Param("codeId") Integer codeId);
}
