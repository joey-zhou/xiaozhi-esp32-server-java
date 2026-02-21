package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.XiaozhiToolMetadata;
import com.xiaozhi.dialogue.service.Persona;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 通过语音切换角色函数
 */
// @Component
public class ChangeRoleFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(ChangeRoleFunction.class);
    @Resource
    private SysRoleService sysRoleService;
    @Resource
    private SysDeviceService sysDeviceService;
    @Resource
    @Lazy
    private ChatService chatService;

    @Override
    public ToolCallback getFunctionCallTool(ChatSession chatSession) {
        SysDevice sysDevice = chatSession.getSysDevice();
        SysRole queryRole = new SysRole();
        queryRole.setUserId(sysDevice.getUserId());
        PageFilter pageFilter = new PageFilter(1, 5);
        List<SysRole> roleList = sysRoleService.query(queryRole, pageFilter);
        if(!roleList.isEmpty() && roleList.size() > 1) {
            return FunctionToolCallback
                    .builder("func_changeRole", (Map<String, String> params, ToolContext toolContext) -> {
                        String roleName = params.get("roleName");
                        try{
                            // 获取参数
                            Optional<SysRole> changedRole = roleList.stream()
                                    .filter(role -> role.getRoleName().equals(roleName))
                                    .findFirst();


                            if(changedRole.isPresent()){
                                SysRole role = changedRole.get();
                                sysDevice.setRoleId(role.getRoleId());
                                sysDeviceService.update(sysDevice);
                                // 切换了角色，需要更换Conversation
                                if(chatSession.getPersona().getConversation()!=null){
                                    chatSession.getPersona().getConversation().clear();
                                }

                                Persona persona = chatService.buildPersona(chatSession, sysDevice, role);
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
                    .toolMetadata(new XiaozhiToolMetadata(true, true))
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

    public String getRoleList(List<SysRole> roleList){
        return roleList.stream().map(SysRole::getRoleName).collect(Collectors.joining(", "));
    }
}
