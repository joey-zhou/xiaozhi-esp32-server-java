package com.xiaozhi.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.ConversationBO;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.dal.mysql.dataobject.ConversationDO;
import com.xiaozhi.message.dal.mysql.mapper.ConversationMapper;
import com.xiaozhi.message.service.ConversationService;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.summary.service.SummaryService;
import com.xiaozhi.utils.DateUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {

    /** 自动标题取第一句话的前这么多个字 */
    static final int AUTO_TITLE_LENGTH = 50;

    private final ConversationMapper conversationMapper;
    private final MessageConvert messageConvert;
    private final MessageService messageService;
    private final SummaryService summaryService;

    public ConversationServiceImpl(ConversationMapper conversationMapper, MessageConvert messageConvert,
                                   MessageService messageService, SummaryService summaryService) {
        this.conversationMapper = conversationMapper;
        this.messageConvert = messageConvert;
        this.messageService = messageService;
        this.summaryService = summaryService;
    }

    @Override
    public PageResult<ConversationBO> page(Integer userId, Integer roleId, int pageNo, int pageSize) {
        IPage<ConversationBO> result = conversationMapper.selectPage(new Page<>(pageNo, pageSize), userId, roleId);
        return new PageResult<>(result.getRecords(), result.getTotal(), pageNo, pageSize);
    }

    @Override
    public ConversationBO get(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        ConversationDO conversation = conversationMapper.selectById(sessionId);
        return conversation == null ? null : messageConvert.toBO(conversation);
    }

    @Override
    public void create(String sessionId, Integer userId, Integer roleId, String firstMessage) {
        LocalDateTime now = DateUtils.now();
        ConversationDO conversation = new ConversationDO();
        conversation.setSessionId(sessionId);
        conversation.setUserId(userId);
        conversation.setRoleId(roleId);
        conversation.setTitle(autoTitle(firstMessage));
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        try {
            conversationMapper.insert(conversation);
        } catch (DuplicateKeyException ignored) {
            // 会话已经建过，保留原来的标题
        }
    }

    @Override
    public void touch(String sessionId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<ConversationDO>()
            .eq(ConversationDO::getSessionId, sessionId)
            .set(ConversationDO::getUpdateTime, DateUtils.now()));
    }

    @Override
    public void rename(Integer userId, String sessionId, String title) {
        int updated = conversationMapper.update(null, new LambdaUpdateWrapper<ConversationDO>()
            .eq(ConversationDO::getSessionId, sessionId)
            .eq(ConversationDO::getUserId, userId)
            .set(ConversationDO::getTitle, title.strip()));
        if (updated == 0) {
            throw new ResourceNotFoundException("会话不存在");
        }
    }

    @Override
    @Transactional
    public List<String> delete(Integer userId, Collection<String> sessionIds) {
        if (userId == null || sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        List<String> owned = conversationMapper.selectList(new LambdaQueryWrapper<ConversationDO>()
                .select(ConversationDO::getSessionId)
                .eq(ConversationDO::getUserId, userId)
                .in(ConversationDO::getSessionId, sessionIds))
            .stream()
            .map(ConversationDO::getSessionId)
            .toList();
        if (owned.isEmpty()) {
            return List.of();
        }
        conversationMapper.delete(new LambdaQueryWrapper<ConversationDO>().in(ConversationDO::getSessionId, owned));
        messageService.deleteBySessionIds(owned);
        summaryService.deleteBySessionIds(owned);
        return owned;
    }

    /** 第一句话去掉换行和多余空白后取开头 */
    static String autoTitle(String firstMessage) {
        if (!StringUtils.hasText(firstMessage)) {
            return null;
        }
        String line = firstMessage.strip().replaceAll("\\s+", " ");
        if (line.codePointCount(0, line.length()) <= AUTO_TITLE_LENGTH) {
            return line;
        }
        return line.substring(0, line.offsetByCodePoints(0, AUTO_TITLE_LENGTH));
    }
}
