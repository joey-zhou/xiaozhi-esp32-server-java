package com.xiaozhi.role;

import com.xiaozhi.common.model.req.RoleCreateReq;
import com.xiaozhi.common.model.req.RolePageReq;
import com.xiaozhi.common.model.req.RoleUpdateReq;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.role.convert.RoleConvert;
import com.xiaozhi.role.domain.Role;
import com.xiaozhi.role.domain.repository.RoleRepository;
import com.xiaozhi.role.domain.vo.AudioConfig;
import com.xiaozhi.role.domain.vo.LlmConfig;
import com.xiaozhi.role.domain.vo.VoiceConfig;
import com.xiaozhi.role.service.RoleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色领域应用服务。
 * <p>
 * 职责：编排 Controller → Domain Service 之间的流程，包括：
 * <ul>
 *   <li>Req/Resp ↔ BO 转换</li>
 *   <li>Req/Resp 转换与业务编排</li>
 * </ul>
 */
@Service
public class RoleAppService {

    @Resource
    private RoleService roleService;

    @Resource
    private RoleRepository roleRepository;

    @Resource
    private RoleConvert roleConvert;

    @Resource
    private DeviceService deviceService;

    public PageResult<RoleResp> page(RolePageReq req, Integer userId) {
        RolePageReq r = req == null ? new RolePageReq() : req;
        return roleService.page(r.getPageNo(), r.getPageSize(),
                r.getRoleId(), r.getRoleName(), r.getIsDefault(), r.getState(), userId)
            .map(roleConvert::toResp);
    }

    @Transactional
    public RoleResp create(RoleCreateReq req, Integer userId) {
        Role role = Role.newRole(userId, req.getRoleName(), req.getRoleDesc(), req.getAvatar(),
                new LlmConfig(req.getModelId(), req.getTemperature(), req.getTopP()),
                new VoiceConfig(req.getTtsId(), req.getSttId(), req.getVoiceName(), req.getTtsPitch(), req.getTtsSpeed(), req.getSttHotwords()),
                new AudioConfig(req.getVadEnergyTh(), req.getVadSpeechTh(), req.getVadSilenceTh(), req.getVadSilenceMs()),
                "1".equals(req.getIsDefault()),
                req.getInactiveTimeoutSeconds());
        roleRepository.save(role);

        return roleConvert.toResp(role);
    }

    @Transactional
    public RoleResp update(Integer roleId, RoleUpdateReq req) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在或无权访问"));

        role.update(req.getRoleName(), req.getRoleDesc(), req.getAvatar(),
                new LlmConfig(req.getModelId(), req.getTemperature(), req.getTopP()),
                new VoiceConfig(req.getTtsId(), req.getSttId(), req.getVoiceName(), req.getTtsPitch(), req.getTtsSpeed(), req.getSttHotwords()),
                new AudioConfig(req.getVadEnergyTh(), req.getVadSpeechTh(), req.getVadSilenceTh(), req.getVadSilenceMs()),
                req.getIsDefault() == null ? null : "1".equals(req.getIsDefault()),
                req.getInactiveTimeoutSeconds());
        
        if (Role.STATE_ENABLED.equals(req.getState())) {
            role.enable();
        } else if (Role.STATE_DISABLED.equals(req.getState())) {
            role.disable();
        }
        roleRepository.save(role);

        return roleConvert.toResp(role);
    }


    /**
     * 删除角色。sys_device.roleId 上没有外键，硬删会留下悬空引用，绑定该角色的设备之后每次接入都会失败，
     * 因此仍有设备绑定时拒绝删除。
     */
    @Transactional
    public void delete(Integer roleId) {
        long boundDevices = countBoundDevices(roleId);
        if (boundDevices > 0) {
            throw new IllegalStateException("该角色仍绑定 " + boundDevices + " 台设备，请先将设备改绑到其他角色");
        }
        roleRepository.delete(roleId);
    }

    private long countBoundDevices(Integer roleId) {
        Long total = deviceService.page(1, 1, null, null, null, null, roleId, null).getTotal();
        return total == null ? 0 : total;
    }
}
