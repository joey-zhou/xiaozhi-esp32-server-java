package com.xiaozhi.server.web.chat;

import com.github.benmanes.caffeine.cache.Ticker;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.MessageAttachmentBO;
import com.xiaozhi.storage.ImageUploadPolicy;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 附件凭 ID 取走，只认自己的、只取一次；过期没发与这一轮没聊成的删文件 */
class WebChatAttachmentServiceTest {

    private final AtomicLong nanos = new AtomicLong();
    private final StorageServiceFactory storageServiceFactory = mock(StorageServiceFactory.class);
    private final StorageService storageService = mock(StorageService.class);
    private WebChatAttachmentService attachments;

    @BeforeEach
    void setUp() throws IOException {
        attachments = new WebChatAttachmentService(nanos::get, Runnable::run);
        ReflectionTestUtils.setField(attachments, "storageServiceFactory", storageServiceFactory);
        ReflectionTestUtils.setField(attachments, "imageUploadPolicy", new ImageUploadPolicy());
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.upload(any(MultipartFile.class), anyString(), anyString()))
                .thenAnswer(invocation -> "uploads/" + invocation.getArgument(1) + "/" + invocation.getArgument(2));
    }

    @Test
    void anImageIsStoredAndHeldForItsOwner() throws IOException {
        WebChatAttachmentService.Pending held = attachments.hold(9, image("花.png", "png"));

        verify(storageService).upload(any(MultipartFile.class), startsWith("chat/"), anyString());
        assertThat(held.kind()).isEqualTo(MessageAttachmentBO.KIND_IMAGE);
        assertThat(held.name()).isEqualTo("花.png");
        assertThat(held.mimeType()).isEqualTo("image/png");
        assertThat(held.path()).startsWith("uploads/chat/").endsWith(".png");
        assertThat(attachments.take(9, List.of(held.id()))).containsExactly(held);
    }

    @Test
    void theMimeTypeComesFromTheBytesNotTheClaim() throws IOException {
        MultipartFile disguised = new MockMultipartFile("file", "a.png", "image/png", encode("jpg"));

        assertThat(attachments.hold(9, disguised).mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void nonImagesAreRejectedBeforeStoring() {
        assertThatThrownBy(() -> attachments.hold(9, new MockMultipartFile("file", "a.png", "image/png", "hi".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storageService);
    }

    @Test
    void anAttachmentIsTakenOnlyOnceAndOnlyByItsOwner() throws IOException {
        WebChatAttachmentService.Pending held = attachments.hold(9, image("花.png", "png"));

        assertThatThrownBy(() -> attachments.take(10, List.of(held.id())))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(attachments.take(9, List.of(held.id()))).containsExactly(held);
        assertThatThrownBy(() -> attachments.take(9, List.of(held.id())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nothingIsTakenWhenOneOfThemIsMissing() throws IOException {
        WebChatAttachmentService.Pending held = attachments.hold(9, image("花.png", "png"));

        assertThatThrownBy(() -> attachments.take(9, List.of(held.id(), "gone")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(attachments.take(9, List.of(held.id()))).containsExactly(held);
    }

    @Test
    void anAttachmentNeverSentIsRemovedFromStorageWhenItExpires() throws IOException {
        WebChatAttachmentService.Pending held = attachments.hold(9, image("花.png", "png"));

        nanos.addAndGet(WebChatAttachmentService.PENDING_TTL.plus(Duration.ofSeconds(1)).toNanos());
        attachments.cleanUp();

        verify(storageServiceFactory).removeFrom(held.path());
        assertThatThrownBy(() -> attachments.take(9, List.of(held.id())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void takenAttachmentsStayInStorageUntilDiscarded() throws IOException {
        WebChatAttachmentService.Pending held = attachments.hold(9, image("花.png", "png"));
        attachments.take(9, List.of(held.id()));

        nanos.addAndGet(WebChatAttachmentService.PENDING_TTL.plus(Duration.ofSeconds(1)).toNanos());
        attachments.cleanUp();
        verify(storageServiceFactory, never()).removeFrom(anyString());

        attachments.discard(List.of(held));
        verify(storageServiceFactory).removeFrom(held.path());
    }

    private static MultipartFile image(String name, String format) throws IOException {
        return new MockMultipartFile("file", name, "image/" + format, encode(format));
    }

    private static byte[] encode(String format) throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
