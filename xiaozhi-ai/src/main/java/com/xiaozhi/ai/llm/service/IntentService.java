package com.xiaozhi.ai.llm.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
/**
 * 意图检测器 — 在调用 LLM 之前，通过关键词匹配检测用户输入的明确意图（<1ms）。
 * <p>
 * 只处理确定性意图（如退出），未来可扩展更多快路径意图。
 */
@Slf4j
@Service
public class IntentService {

    private final ExitKeywordDetector exitKeywordDetector = new ExitKeywordDetector();

    /**
     * 确定性意图枚举。未来可扩展：HELP、RESET、SWITCH_ROLE 等。
     */
    public enum Intent {
        /** 退出对话 */
        EXIT,
        /** 无特殊意图，继续正常流程 */
        NONE
    }

    /**
     * 检测用户输入的明确意图。
     *
     * @param userText 用户输入文本
     * @return 检测到的意图，无特殊意图返回 NONE
     */
    public Intent detect(String userText) {
        if (!StringUtils.hasText(userText)) {
            return Intent.NONE;
        }

        // 退出意图
        if (exitKeywordDetector.detectExitIntent(userText)) {
            log.info("检测到退出意图: \"{}\"", userText);
            return Intent.EXIT;
        }

        // 未来可在此扩展更多意图检测

        return Intent.NONE;
    }
}
