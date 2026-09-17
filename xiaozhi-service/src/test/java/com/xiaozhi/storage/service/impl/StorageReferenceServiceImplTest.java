package com.xiaozhi.storage.service.impl;

import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住存量统计的归属判据只有地址前缀：本地存储没有前缀，历史相对路径不会因为换存储而失效，
 * 这条保证了「本地切到对象存储」这个无害方向不会被拦下来。
 */
@ExtendWith(MockitoExtension.class)
class StorageReferenceServiceImplTest {

    private static final String CLOUD_PREFIX = "https://bucket.cos.ap-beijing.myqcloud.com/";

    @Mock
    private StorageServiceFactory storageServiceFactory;

    @Mock
    private StorageService storageService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private StorageReferenceServiceImpl storageReferenceService;

    @Test
    void localStorageHasNoPrefixSoNothingIsCounted() {
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.urlPrefix()).thenReturn("");

        assertThat(storageReferenceService.countOnCurrentStorage()).isZero();
        verifyNoInteractions(messageService);
    }

    @Test
    void cloudStoragePrefixIsCounted() {
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.urlPrefix()).thenReturn(CLOUD_PREFIX);
        when(messageService.countStoredPathsWithPrefix(CLOUD_PREFIX)).thenReturn(1200L);

        assertThat(storageReferenceService.countOnCurrentStorage()).isEqualTo(1200L);
        verify(messageService).countStoredPathsWithPrefix(CLOUD_PREFIX);
    }

    /** 当前配置坏到建不出客户端时不能反过来拦住用户，否则连改回可用配置都做不到 */
    @Test
    void unusableCurrentStorageIsCountedAsZeroInsteadOfBlocking() {
        when(storageServiceFactory.getStorageService()).thenThrow(new IllegalStateException("endpoint 填错了"));

        assertThat(storageReferenceService.countOnCurrentStorage()).isZero();
        verifyNoInteractions(messageService);
    }
}
