package com.xiaozhi.service.impl;

import com.xiaozhi.common.cache.CacheHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.repository.SysRoleRepository;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色操作
 *
 * @author Joey
 */
@Service
public class SysRoleServiceImpl extends BaseServiceImpl implements SysRoleService {
    private final static String CACHE_NAME = "XiaoZhi:SysRole";

    @Resource
    private SysRoleRepository roleRepository;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    @Override
    @Transactional
    public int add(SysRole role) {
        if (role.getIsDefault() != null && role.getIsDefault().equals("1")) {
            roleRepository.resetDefault(role);
        }
        return roleRepository.add(role);
    }

    @Override
    public List<SysRole> query(SysRole role, PageFilter pageFilter) {
        if (pageFilter != null) {
            return roleRepository.findRoles(
                    role.getUserId(),
                    role.getState(),
                    role.getIsDefault(),
                    org.springframework.data.domain.PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit())
            ).getContent();
        }
        return roleRepository.query(role);
    }

    @Override
    @Transactional
    public int update(SysRole role) {
        if (role.getIsDefault() != null && role.getIsDefault().equals("1")) {
            roleRepository.resetDefault(role);
        }
        return roleRepository.update(role);
    }

    @Override
    @Transactional
    public int deleteById(Integer roleId) {
        return roleRepository.deleteRoleById(roleId);
    }

    @Override
    public SysRole selectRoleById(Integer roleId) {
        return roleRepository.selectRoleById(roleId);
    }
}
