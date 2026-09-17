package com.xiaozhi.agent.convert;

import com.xiaozhi.authrole.convert.AuthRoleConvert;
import com.xiaozhi.authrole.dal.mysql.dataobject.AuthRoleDO;
import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.bo.AuthRoleBO;
import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.common.model.bo.PermissionBO;
import com.xiaozhi.common.model.bo.UserAuthBO;
import com.xiaozhi.common.model.resp.AgentResp;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.PermissionTreeResp;
import com.xiaozhi.operationlog.convert.OperationLogConvert;
import com.xiaozhi.operationlog.dal.mysql.dataobject.OperationLogDO;
import com.xiaozhi.permission.convert.PermissionConvert;
import com.xiaozhi.permission.dal.mysql.dataobject.PermissionDO;
import com.xiaozhi.userauth.convert.UserAuthConvert;
import com.xiaozhi.userauth.dal.mysql.dataobject.UserAuthDO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 六个逐字段直传的转换器，此前在整个测试套件里一次都没被真正执行过——
 * MapStruct 只在源、目标字段名一致时才生成搬运代码，改名一侧不改另一侧时
 * 它既不报错也不搬运，字段静默变 null。这里把每个转换器至少跑通一遍并断言关键字段。
 */
class PlainConvertMappingTest {

    @Test
    void agentBoToRespKeepsBothTheConfigAndAgentIdentity() {
        AgentBO bo = new AgentBO();
        bo.setConfigId(3);
        bo.setUserId(7);
        bo.setConfigName("我的智能体");
        bo.setProvider("coze");
        bo.setAgentName("助手");
        bo.setBotId("bot-1");
        bo.setIconUrl("https://example.com/i.png");

        AgentResp resp = Mappers.getMapper(AgentConvert.class).toResp(bo);

        assertThat(resp.getConfigId()).isEqualTo(3);
        assertThat(resp.getUserId()).isEqualTo(7);
        assertThat(resp.getConfigName()).isEqualTo("我的智能体");
        assertThat(resp.getProvider()).isEqualTo("coze");
        assertThat(resp.getAgentName()).isEqualTo("助手");
        assertThat(resp.getBotId()).isEqualTo("bot-1");
        assertThat(resp.getIconUrl()).isEqualTo("https://example.com/i.png");
    }

    @Test
    void authRoleDoToBoToRespKeepsTheRoleKey() {
        AuthRoleConvert convert = Mappers.getMapper(AuthRoleConvert.class);
        AuthRoleDO source = new AuthRoleDO();
        source.setAuthRoleId(1);
        source.setAuthRoleName("管理员");
        source.setRoleKey("admin");
        source.setDescription("全部权限");
        source.setStatus("1");

        AuthRoleBO bo = convert.toBO(source);
        AuthRoleResp resp = convert.toResp(bo);

        assertThat(bo.getRoleKey()).isEqualTo("admin");
        assertThat(resp.getAuthRoleId()).isEqualTo(1);
        assertThat(resp.getAuthRoleName()).isEqualTo("管理员");
        assertThat(resp.getRoleKey())
            .as("roleKey 是鉴权判定用的，丢了整个角色就等于没有任何权限标识")
            .isEqualTo("admin");
        assertThat(resp.getStatus()).isEqualTo("1");
    }

    @Test
    void permissionDoToBoLeavesChildrenForTheTreeBuilder() {
        PermissionConvert convert = Mappers.getMapper(PermissionConvert.class);
        PermissionDO source = new PermissionDO();
        source.setPermissionId(10);
        source.setParentId(1);
        source.setName("设备管理");
        source.setPermissionKey("system:device:api:list");
        source.setPermissionType("api");
        source.setSort(3);

        PermissionBO bo = convert.toBO(source);

        assertThat(bo.getPermissionId()).isEqualTo(10);
        assertThat(bo.getParentId()).isEqualTo(1);
        assertThat(bo.getPermissionKey()).isEqualTo("system:device:api:list");
        assertThat(bo.getPermissionType()).isEqualTo("api");
        assertThat(bo.getSort()).isEqualTo(3);
        assertThat(bo.getChildren()).as("子节点由建树逻辑填，转换器不许自己造").isNull();
    }

    @Test
    void permissionBoToTreeRespCarriesChildren() {
        PermissionConvert convert = Mappers.getMapper(PermissionConvert.class);
        PermissionBO child = new PermissionBO();
        child.setPermissionId(11);
        child.setName("查看设备");
        PermissionBO parent = new PermissionBO();
        parent.setPermissionId(10);
        parent.setName("设备管理");
        parent.setChildren(List.of(child));

        PermissionTreeResp resp = convert.toTreeResp(parent);

        assertThat(resp.getChildren()).hasSize(1);
        assertThat(resp.getChildren().get(0).getName()).isEqualTo("查看设备");
    }

    @Test
    void operationLogBoToDoKeepsTheFailureDetails() {
        OperationLogBO bo = new OperationLogBO();
        bo.setUserId(7);
        bo.setIp("127.0.0.1");
        bo.setModule("用户管理");
        bo.setOperation("用户登录");
        bo.setMethod("POST");
        bo.setUrl("/api/user/login");
        bo.setSuccess(false);
        bo.setErrorMsg("密码错误");
        bo.setCostMs(12);

        OperationLogDO d = Mappers.getMapper(OperationLogConvert.class).toDO(bo);

        assertThat(d.getUserId()).isEqualTo(7);
        assertThat(d.getModule()).isEqualTo("用户管理");
        assertThat(d.getUrl()).isEqualTo("/api/user/login");
        assertThat(d.getSuccess()).isFalse();
        assertThat(d.getErrorMsg())
            .as("失败原因不落库，审计日志就只剩一条「失败了」")
            .isEqualTo("密码错误");
        assertThat(d.getCostMs()).isEqualTo(12);
    }

    @Test
    void userAuthRoundTripsTheOpenIdBinding() {
        UserAuthConvert convert = Mappers.getMapper(UserAuthConvert.class);
        UserAuthDO source = new UserAuthDO();
        source.setId(1L);
        source.setUserId(7);
        source.setOpenId("open-1");
        source.setUnionId("union-1");
        source.setPlatform("wechat");
        source.setProfile("{\"nickname\":\"小明\"}");

        UserAuthBO bo = convert.toBO(source);

        assertThat(bo.getUserId()).isEqualTo(7);
        assertThat(bo.getOpenId())
            .as("openId 丢了，这个微信号下次登录会被当成新用户，旧账号再也回不去")
            .isEqualTo("open-1");
        assertThat(bo.getUnionId()).isEqualTo("union-1");
        assertThat(bo.getPlatform()).isEqualTo("wechat");
        assertThat(bo.getProfile()).isEqualTo("{\"nickname\":\"小明\"}");

        UserAuthDO back = convert.toDO(bo);
        assertThat(back.getOpenId()).isEqualTo("open-1");
        assertThat(back.getPlatform()).isEqualTo("wechat");
    }
}
