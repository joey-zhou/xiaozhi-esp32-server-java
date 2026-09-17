package com.xiaozhi.storage.service.impl;

import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.storage.service.StorageReferenceService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StorageReferenceServiceImpl implements StorageReferenceService {

    @Resource
    private StorageServiceFactory storageServiceFactory;

    @Resource
    private MessageService messageService;

    @Override
    public long countOnCurrentStorage() {
        // 前缀取自当前生效的实现而不是待写入的新配置：要数的是「换掉之后会失效的存量」，
        // 归属只能靠地址前缀认，本地存储没有前缀，条件不成立直接是 0
        String prefix;
        try {
            prefix = storageServiceFactory.getStorageService().urlPrefix();
        } catch (Exception e) {
            // 当前配置本身已经建不出客户端（字段填错、凭证残缺）时数不出存量。
            // 这里必须放行：拦住只会让用户连改回可用配置的路都没有
            log.warn("当前存储配置不可用，跳过存量统计", e);
            return 0;
        }
        if (prefix == null || prefix.isEmpty()) {
            return 0;
        }
        return messageService.countStoredPathsWithPrefix(prefix);
    }
}
