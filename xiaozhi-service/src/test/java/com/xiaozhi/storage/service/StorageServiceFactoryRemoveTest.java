package com.xiaozhi.storage.service;

import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.storage.service.impl.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 钉住删存储的判空集中在 removeFrom：调用方不再各自判空，空路径必须在这里挡掉，
 * 否则空串落到本地实现会被解析成数据根目录。
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceFactoryRemoveTest {

    @Mock
    private ConfigService configService;

    @Mock
    private LocalStorageService localStorageService;

    private StorageServiceFactory factory;

    @BeforeEach
    void setUp() {
        factory = new StorageServiceFactory();
        ReflectionTestUtils.setField(factory, "configService", configService);
        ReflectionTestUtils.setField(factory, "localStorageService", localStorageService);
    }

    @Test
    void blankPathTouchesNoStorage() {
        factory.removeFrom(null);
        factory.removeFrom("");
        factory.removeFrom("   ");

        verifyNoInteractions(localStorageService, configService);
    }

    @Test
    void relativePathIsRemovedFromLocalStorageWithoutReadingConfig() {
        factory.removeFrom("audio/2026-01-01/a.wav");

        verify(localStorageService).remove("audio/2026-01-01/a.wav");
        verifyNoInteractions(configService);
    }
}
