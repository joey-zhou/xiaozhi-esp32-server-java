package com.xiaozhi.file;

import com.xiaozhi.common.web.LocalFileUrlPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 拦截器交给签名策略的路径，必须与 Spring 选静态资源时用的是同一个。
 * <p>
 * 拦截器按 /audio/**、/uploads/** 挂载，PathPatternParser 按<b>解码后</b>的段匹配；
 * 若这里拿未解码的 getRequestURI()，/%75ploads/x.wav 会被策略判成「不受保护」而放行，
 * 随后资源处理器照常按解码后的段吐出文件——整套时效签名在这条路径上等于没开。
 */
@ExtendWith(MockitoExtension.class)
class LocalFileAccessInterceptorTest {

    private static final String PROTECTED_FILE = "uploads/firmware/app.bin";

    @Mock
    private LocalFileUrlPolicy localFileUrlPolicy;

    private LocalFileAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new LocalFileAccessInterceptor();
        ReflectionTestUtils.setField(interceptor, "localFileUrlPolicy", localFileUrlPolicy);
    }

    /** 未签名的受保护路径直接 403，这是基线 */
    @Test
    void unsignedProtectedPathIsRejected() throws Exception {
        givenUnsignedIsRejected();

        assertThat(handle("/uploads/firmware/app.bin")).isFalse();
    }

    /**
     * 编码、matrix 参数、重复斜杠、尾斜杠都不能绕过校验。
     * 这几种写法浏览器与资源处理器都会解析成同一个文件，校验却可能看成另一个路径。
     */
    @Test
    void encodedAndDecoratedPathsCannotBypassTheCheck() throws Exception {
        givenUnsignedIsRejected();

        for (String uri : List.of(
                "/%75ploads/firmware/app.bin",      // u -> %75
                "/uploads/firmware/%61pp.bin",      // a -> %61
                "/uploads;a=b/firmware/app.bin",    // matrix 参数
                "/uploads/firmware/app.bin;a=b",
                "/uploads/firmware/app.bin/")) {    // 尾斜杠
            MockHttpServletResponse response = new MockHttpServletResponse();
            boolean allowed = interceptor.preHandle(request(uri), response, new Object());

            assertThat(allowed).as("%s 必须被拦下", uri).isFalse();
            assertThat(response.getStatus()).as("%s 的状态码", uri).isEqualTo(403);
        }
    }

    /** 无论用哪种写法，交给签名策略的都必须是同一个归一后的路径 */
    @Test
    void policyAlwaysSeesTheSameCanonicalPath() throws Exception {
        givenUnsignedIsRejected();

        handle("/%75ploads/firmware/app.bin");
        handle("/uploads;a=b/firmware/app.bin");
        handle("/uploads/firmware/app.bin/");

        verify(localFileUrlPolicy, times(3))
                .verify(eq(PROTECTED_FILE), any(), any());
    }

    /** 带上合法签名就放行，签名参数要原样透给策略 */
    @Test
    void signedRequestPasses() throws Exception {
        when(localFileUrlPolicy.verify(PROTECTED_FILE, "123", "abc")).thenReturn(true);

        MockHttpServletRequest request = request("/uploads/firmware/app.bin");
        request.setParameter("exp", "123");
        request.setParameter("sig", "abc");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    /** 预检请求不带签名参数，拦下来会让跨域访问整体不可用 */
    @Test
    void optionsPreflightIsNotBlocked() throws Exception {
        MockHttpServletRequest request = request("/uploads/firmware/app.bin");
        request.setMethod("OPTIONS");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    /** 策略抛异常时按拒绝处理，不能因为内部错误就放行 */
    @Test
    void policyFailureIsTreatedAsRejection() throws Exception {
        when(localFileUrlPolicy.verify(any(), any(), any())).thenThrow(new IllegalStateException("密钥未初始化"));

        assertThat(handle("/uploads/firmware/app.bin")).isFalse();
    }

    /**
     * 照搬真实策略的语义：受保护前缀下没签名就拒，**其余路径一律放行**。
     * 后半句是这组用例的关键——绕过之所以成立，正是因为策略看到 %75ploads/… 时认为「不受保护」而放行。
     * 若把 mock 写成「只有 PROTECTED_FILE 返回 false，其余走 Mockito 默认的 false」，
     * 绕过发生时测试反而会通过。
     */
    private void givenUnsignedIsRejected() {
        when(localFileUrlPolicy.verify(any(), any(), any())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            boolean protectedPath = path.startsWith("uploads/") || path.startsWith("audio/");
            return !protectedPath;
        });
    }

    private boolean handle(String uri) throws Exception {
        return interceptor.preHandle(request(uri), new MockHttpServletResponse(), new Object());
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
