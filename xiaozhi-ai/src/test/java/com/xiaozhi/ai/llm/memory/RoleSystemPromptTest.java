package com.xiaozhi.ai.llm.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色系统提示词由模板渲染：角色设定和位置按需拼入，设备对话约束和消息前缀说明始终存在。
 */
class RoleSystemPromptTest {

    private static SystemMessage prompt(String roleDesc, String location) {
        return new Conversation("device", 1, "session", roleDesc, 1)
                .roleSystemMessage(new ConversationContext(location));
    }

    @Test
    void roleDescriptionAndLocationAreRendered() {
        String text = prompt("你是小智，一个爱讲冷笑话的助手。", "北京市海淀区").getText();

        assertThat(text).startsWith("角色设定：" + System.lineSeparator() + "你是小智，一个爱讲冷笑话的助手。");
        assertThat(text).contains("当前位置：北京市海淀区。");
        assertThat(text).endsWith("以用户说的为准。");
    }

    @Test
    void constraintsAndPrefixRulesAlwaysPresent() {
        String text = prompt(null, null).getText();

        assertThat(text).startsWith("用户主要通过语音设备和你交谈");
        assertThat(text).doesNotContain("角色设定：").doesNotContain("当前位置：");
        assertThat(text).contains("[yyyy-MM-ddTHH:mm:ss]").contains("[neutral]").doesNotContain("说话人");
        assertThat(text).contains("不用括号写动作");
    }

    @Test
    void roleDescriptionWithDelimiterCharactersIsKeptVerbatim() {
        String text = prompt("报价固定 $100 {含税}，<不议价>", null).getText();

        assertThat(text).contains("报价固定 $100 {含税}，<不议价>");
    }

    // 系统提示词会话内必须保持不变，逐轮变化的元数据（时间戳等）只能进 UserMessage 前缀。
    // 模板里只允许出现格式说明 [yyyy-MM-ddTHH:mm:ss]，不能渲染出真实时间戳。
    @Test
    void promptCarriesNoRenderedTimestamp() {
        String text = prompt("你是小智，一个爱讲冷笑话的助手。", "北京市海淀区").getText();

        assertThat(text).doesNotContainPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThat(text).contains("[yyyy-MM-ddTHH:mm:ss]");
    }
}
