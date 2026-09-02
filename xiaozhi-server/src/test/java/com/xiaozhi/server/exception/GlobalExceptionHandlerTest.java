package com.xiaozhi.server.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集中钉住异常到 HTTP 状态码与响应体（code/message）的映射，各 Controller 测试只断言状态码即可。
 * 其中带 message 参数的异常必须原样透传业务文案，message 为空时回退到各自的兜底文案。
 */
class GlobalExceptionHandlerTest extends ControllerTestSupport {

    private final ProbeController controller = new ProbeController();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(controller);
    }

    @ParameterizedTest
    @MethodSource("exceptionMappings")
    void mapsExceptionToStatusAndBody(RuntimeException thrown, int status, String message) throws Exception {
        controller.toThrow = thrown;

        mockMvc.perform(get("/probe/throw"))
            .andExpect(status().is(status))
            .andExpect(jsonPath("$.code").value(status))
            .andExpect(jsonPath("$.message").value(message));
    }

    private static Stream<Arguments> exceptionMappings() {
        return Stream.of(
            Arguments.of(new UsernameNotFoundException("alice"), 400, "用户名不存在"),
            Arguments.of(new UserPasswordNotMatchException("bad"), 400, "用户密码不正确"),
            Arguments.of(new UnauthorizedException("无权访问该配置"), 403, "无权访问该配置"),
            Arguments.of(new NotLoginException("未提供token", "login", NotLoginException.NOT_TOKEN),
                401, "登录已过期，请重新登录"),
            Arguments.of(new NotPermissionException("system:config:api:list"), 403, "权限不足"),
            Arguments.of(new NotRoleException("admin"), 403, "角色权限不足"),
            Arguments.of(new ResourceNotFoundException("配置不存在或无权访问"), 404, "配置不存在或无权访问"),
            Arguments.of(new IllegalArgumentException("设备ID不正确"), 400, "设备ID不正确"),
            Arguments.of(new IllegalArgumentException(""), 400, "请求参数不合法"),
            Arguments.of(new IllegalStateException("MCP服务代码重复"), 409, "MCP服务代码重复"),
            Arguments.of(new IllegalStateException(""), 409, "当前状态不允许此操作"),
            Arguments.of(new OperationFailedException("保存MCP工具排除配置失败"), 500, "保存MCP工具排除配置失败"),
            Arguments.of(new OperationFailedException(""), 500, "操作失败，请稍后重试"),
            // 未单独映射的运行时异常统一兜底，内部细节不外泄
            Arguments.of(new RuntimeException("不该外泄的内部细节"), 500, "服务器错误，请联系管理员")
        );
    }

    @Test
    void reportsMissingRequestParameterByName() throws Exception {
        mockMvc.perform(get("/probe/param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("缺少必要参数: name"));
    }

    @Test
    void reportsTypeMismatchedPathVariableByName() throws Exception {
        mockMvc.perform(get("/probe/id/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("参数类型不合法: id"));
    }

    @Test
    void reportsUnreadableRequestBody() throws Exception {
        mockMvc.perform(post("/probe/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("请求体格式不正确"));
    }

    @Test
    void reportsUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(post("/probe/param").param("name", "alice"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value(405))
            .andExpect(jsonPath("$.message").value("请求方法不支持"));
    }

    /** 只为触发异常存在的探针端点，不对应任何生产路由 */
    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        private RuntimeException toThrow;

        @GetMapping("/throw")
        public ApiResponse<Void> alwaysThrows() {
            throw toThrow;
        }

        @GetMapping("/param")
        public ApiResponse<Void> requiresParam(@RequestParam("name") String name) {
            return ApiResponse.success();
        }

        @GetMapping("/id/{id}")
        public ApiResponse<Void> requiresLongPathVariable(@PathVariable("id") Long id) {
            return ApiResponse.success();
        }

        @PostMapping("/body")
        public ApiResponse<Void> requiresJsonBody(@RequestBody Map<String, String> body) {
            return ApiResponse.success();
        }
    }
}
