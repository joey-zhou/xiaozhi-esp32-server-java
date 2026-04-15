package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.dialogue.llm.factory.PersonaFactory;
import com.xiaozhi.ai.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.ai.llm.tool.XiaozhiToolMetadata;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.role.service.RoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 通过语音切换角色函数
 */
@Component
public class ChangeRoleFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(ChangeRoleFunction.class);
    private static final String TOOL_NAME = "change_role";
    @Resource
    private RoleService roleService;
    @Resource
    private DeviceRepository deviceRepository;
    @Resource
    @Lazy
    private PersonaFactory personaFactory;
    @Resource
    private SessionManager sessionManager;

    @Override
    public ToolCallback getFunctionCallTool(ToolSession toolSession) {
        // 通过 sessionManager 获取 ChatSession（ToolSession 是 Adapter，不能直接强转）
        ChatSession chatSession = sessionManager.getSession(toolSession.getSessionId());
        if (chatSession == null) {
            return null;
        }
        DeviceBO device = chatSession.getDevice();
        List<RoleBO> roleList = roleService.listBO(device.getUserId(), 5);
        if(!roleList.isEmpty() && roleList.size() > 1) {
            return FunctionToolCallback
                    .builder(TOOL_NAME, (Map<String, String> params, ToolContext toolContext) -> {
                        String roleName = params.get("roleName");
                        try{
                            // 获取参数
                            Optional<RoleBO> changedRole = roleList.stream()
                                    .filter(role -> role.getRoleName().equals(roleName))
                                    .findFirst();


                            if(changedRole.isPresent()){
                                RoleBO role = changedRole.get();
                                deviceRepository.findById(device.getDeviceId()).ifPresent(d -> {
                                    d.bindRole(role.getRoleId());
                                    deviceRepository.save(d);
                                });
                                device.setRoleId(role.getRoleId());
                                device.setRoleName(role.getRoleName());
                                // 切换了角色，需要更换Conversation
                                if(chatSession.getPersona().getConversation()!=null){
                                    chatSession.getPersona().getConversation().clear();
                                }

                                Persona persona = personaFactory.buildPersona(chatSession, device, role);
                                chatSession.setPersona(persona);
                                return "角色已切换至" + roleName;
                            }else{
                                return "角色切换失败, 没有对应角色哦";
                            }
                        }catch (Exception e){
                            logger.error("角色切换异常，role name: {}", roleName, e);
                            return "角色切换异常";
                        }
                    })
                    .toolMetadata(new XiaozhiToolMetadata(true))
                    .description("当用户想切换角色/助手名字时调用,可选的角色名称列表：" + getRoleList(roleList)
                            + ". 调用前需要先把所有角色名称告知用户,用户告诉你角色名称进行切换.")
                    .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "roleName": {
                                    "type": "string",
                                    "description": "要切换的角色名称"
                                }
                            },
                            "required": ["roleName"]
                        }
                    """)
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();
        }
        return null;
    }

    public String getRoleList(List<RoleBO> roleList){
        return roleList.stream().map(RoleBO::getRoleName).collect(Collectors.joining(", "));
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public String getToolDescription() {
        return "切换角色";
    }
}
