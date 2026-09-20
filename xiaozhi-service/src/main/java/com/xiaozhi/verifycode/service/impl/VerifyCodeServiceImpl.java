package com.xiaozhi.verifycode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.utils.DateUtils;
import com.xiaozhi.verifycode.convert.VerifyCodeConvert;
import com.xiaozhi.verifycode.dal.mysql.dataobject.VerifyCodeDO;
import com.xiaozhi.verifycode.dal.mysql.mapper.VerifyCodeMapper;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class VerifyCodeServiceImpl implements VerifyCodeService {

    private static final int VALID_MINUTES = 10;

    /** 同一账号在有效期窗口内允许的校验次数，达到即作废该账号所有未过期的码。 */
    private static final int MAX_ATTEMPTS = 5;

    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(VALID_MINUTES);

    private static final String ATTEMPT_KEY_PREFIX = "xiaozhi:captcha:attempt:";

    @Resource
    private VerifyCodeMapper verifyCodeMapper;

    @Resource
    private VerifyCodeConvert verifyCodeConvert;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private StorageServiceFactory storageServiceFactory;

    @Override
    public VerifyCodeBO findValid(String code, String deviceId, String sessionId) {
        // 三个维度全空时查询会退化成「有效期内最新一条」，跨设备匹配，直接拒绝
        if (!StringUtils.hasLength(code) && !StringUtils.hasLength(deviceId) && !StringUtils.hasLength(sessionId)) {
            return null;
        }
        VerifyCodeDO verifyCode = verifyCodeMapper.selectOne(deviceCodeQuery(code, deviceId, sessionId)
            .orderByDesc(VerifyCodeDO::getCreateTime)
            .last("LIMIT 1"));
        return verifyCodeConvert.toBO(verifyCode);
    }

    @Override
    public VerifyCodeBO findValidByCode(String code) {
        if (!StringUtils.hasLength(code)) {
            return null;
        }
        List<VerifyCodeDO> matched = verifyCodeMapper.selectList(deviceCodeQuery(code, null, null)
            .last("LIMIT 2"));
        // 同一个码命中多条时无法判定属于哪台设备，一律拒绝，由用户重新获取
        if (matched.size() != 1) {
            return null;
        }
        return verifyCodeConvert.toBO(matched.get(0));
    }

    /** 设备码查询条件：非空维度才参与过滤；account 为空把账号码那半张表排除在外。 */
    private LambdaQueryWrapper<VerifyCodeDO> deviceCodeQuery(String code, String deviceId, String sessionId) {
        return new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(StringUtils.hasLength(deviceId), VerifyCodeDO::getDeviceId, deviceId)
            .eq(StringUtils.hasLength(sessionId), VerifyCodeDO::getSessionId, sessionId)
            .eq(StringUtils.hasLength(code), VerifyCodeDO::getCode, code)
            .isNull(VerifyCodeDO::getAccount)
            .ge(VerifyCodeDO::getCreateTime, validSince());
    }

    @Override
    public int createForDevice(String deviceId, String sessionId, String type, String code) {
        VerifyCodeDO verifyCode = new VerifyCodeDO();
        verifyCode.setDeviceId(deviceId);
        verifyCode.setSessionId(sessionId);
        verifyCode.setType(type);
        verifyCode.setCode(code);
        verifyCode.setCreateTime(DateUtils.now());
        return verifyCodeMapper.insert(verifyCode);
    }

    @Override
    public int deleteByDeviceId(String deviceId) {
        List<String> audioPaths = audioPathsOf(new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getDeviceId, deviceId));
        int deleted = verifyCodeMapper.delete(new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getDeviceId, deviceId));
        removeAudios(audioPaths);
        return deleted;
    }

    /**
     * 只取有音频的那些行的路径；验证码音频每次合成一个独立文件，不存在多行共用。
     * <p>
     * 只 select 了 audioPath 一列，该列为 NULL 时整行为空行，MyBatis 返回的是 null 对象而不是属性为 null 的对象。
     */
    private List<String> audioPathsOf(LambdaQueryWrapper<VerifyCodeDO> scope) {
        return verifyCodeMapper.selectList(scope.select(VerifyCodeDO::getAudioPath))
            .stream()
            .filter(Objects::nonNull)
            .map(VerifyCodeDO::getAudioPath)
            .filter(StringUtils::hasText)
            .toList();
    }

    /**
     * 删验证码音频文件。
     * <p>
     * 调用方是设备绑定成功与删除设备两条路径，删文件失败不该让它们失败——
     * 存储抖动时最坏只是留一个孤儿文件，而抛出去会让新设备直接绑不上。
     */
    private void removeAudios(List<String> audioPaths) {
        for (String audioPath : audioPaths) {
            try {
                storageServiceFactory.removeFrom(audioPath);
            } catch (Exception e) {
                log.warn("删除验证码音频失败，文件将残留: {}", audioPath, e);
            }
        }
    }

    @Override
    public int updateAudioPath(String deviceId, String sessionId, String code, String audioPath) {
        return verifyCodeMapper.update(null, new LambdaUpdateWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getDeviceId, deviceId)
            .eq(VerifyCodeDO::getSessionId, sessionId)
            .eq(VerifyCodeDO::getCode, code)
            .set(VerifyCodeDO::getAudioPath, audioPath));
    }

    @Override
    public String generateForAccount(String account) {
        if (!StringUtils.hasText(account)) {
            throw new IllegalArgumentException("账号不能为空");
        }
        deleteByAccount(account);
        resetAttempts(account);

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        VerifyCodeDO verifyCode = new VerifyCodeDO();
        verifyCode.setAccount(account);
        verifyCode.setCode(code);
        verifyCode.setCreateTime(DateUtils.now());
        if (verifyCodeMapper.insert(verifyCode) <= 0) {
            throw new IllegalStateException("生成验证码失败");
        }
        return code;
    }

    @Override
    public boolean consumeByAccount(String account, String code) {
        if (!StringUtils.hasText(account) || !StringUtils.hasText(code)) {
            return false;
        }

        String attemptKey = attemptKey(account);
        Long attempts = stringRedisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1L) {
            stringRedisTemplate.expire(attemptKey, ATTEMPT_WINDOW);
        }
        if (attempts == null || attempts > MAX_ATTEMPTS) {
            deleteByAccount(account);
            return false;
        }

        // delete 的影响行数天然是原子的，并发只有一个调用方能拿到 1，重放拿到 0
        int consumed = verifyCodeMapper.delete(new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getAccount, account)
            .eq(VerifyCodeDO::getCode, code)
            .ge(VerifyCodeDO::getCreateTime, validSince()));
        if (consumed > 0) {
            stringRedisTemplate.delete(attemptKey);
            return true;
        }

        if (attempts == MAX_ATTEMPTS) {
            deleteByAccount(account);
        }
        return false;
    }

    /** 清零该账号的校验次数，重新发码后必须调用，否则新码会被上一轮攒下的次数直接判死。 */
    private void resetAttempts(String account) {
        if (!StringUtils.hasText(account)) {
            return;
        }
        stringRedisTemplate.delete(attemptKey(account));
    }

    private String attemptKey(String account) {
        return ATTEMPT_KEY_PREFIX + account;
    }

    /** 删除该账号名下所有验证码，不看有效期。 */
    private int deleteByAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return 0;
        }
        return verifyCodeMapper.delete(new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getAccount, account));
    }

    /** 验证码有效期起点。 */
    private LocalDateTime validSince() {
        return DateUtils.now().minusMinutes(VALID_MINUTES);
    }

    @Override
    public int deleteExpired(int expiredBeforeMinutes, int batchSize) {
        LocalDateTime expireBefore = DateUtils.now().minusMinutes(expiredBeforeMinutes);
        int deleted = 0;
        while (true) {
            List<VerifyCodeDO> batch = verifyCodeMapper.selectList(new LambdaQueryWrapper<VerifyCodeDO>()
                .select(VerifyCodeDO::getCodeId, VerifyCodeDO::getAudioPath)
                .lt(VerifyCodeDO::getCreateTime, expireBefore)
                .orderByAsc(VerifyCodeDO::getCodeId)
                .last("LIMIT " + batchSize));
            if (batch.isEmpty()) {
                break;
            }
            List<Integer> codeIds = batch.stream().map(VerifyCodeDO::getCodeId).toList();
            List<String> audioPaths = batch.stream()
                .map(VerifyCodeDO::getAudioPath)
                .filter(StringUtils::hasText)
                .toList();
            verifyCodeMapper.delete(new LambdaQueryWrapper<VerifyCodeDO>().in(VerifyCodeDO::getCodeId, codeIds));
            removeAudios(audioPaths);
            deleted += codeIds.size();
        }
        return deleted;
    }
}
