package com.xiaozhi.ai.llm.intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 意图检测器
 * 在调用 LLM 之前检测用户输入的明确意图
 * 对于某些明确的意图（如退出），直接处理而不经过 LLM
 */
@Component
public class IntentDetector {
    private static final Logger logger = LoggerFactory.getLogger(IntentDetector.class);

    private final ExitKeywordDetector exitKeywordDetector;

    public IntentDetector() {
        this.exitKeywordDetector = new ExitKeywordDetector();
    }

    /**
     * 检测用户输入的意图
     * 
     * @param userInput 用户输入文本
     * @return 检测到的意图，如果没有明确意图返回 null
     */
    public UserIntent detectIntent(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return null;
        }

        // 检测退出意图
        if (exitKeywordDetector.detectExitIntent(userInput)) {
            logger.info("检测到退出意图: \"{}\"", userInput);
            return new ExitIntent();
        }

        // 未来可以在这里添加更多意图检测
        // 例如：欢迎意图、帮助意图等

        return null;
    }

    /**
     * 用户意图接口
     */
    public interface UserIntent {
        /**
         * 获取意图类型
         */
        String getType();
    }

    /**
     * 退出意图
     */
    public static class ExitIntent implements UserIntent {
        @Override
        public String getType() {
            return "EXIT";
        }
    }
}

