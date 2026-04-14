package com.xiaozhi.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.servers.Server;

import java.util.List;

/**
 * Knife4j (Swagger) API文档配置
 *
 * 访问地址:
 * - Knife4j UI: http://localhost:端口/doc.html
 * - Swagger UI: http://localhost:端口/swagger-ui/index.html
 * - OpenAPI JSON: http://localhost:端口/v3/api-docs
 *
 * @author Joey
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("小智物联网平台 API")
                        .description("小智ESP32智能物联网管理平台的RESTful API文档\n\n" +
                                "### 功能模块\n" +
                                "- **用户管理**: 用户注册、登录、权限管理\n" +
                                "- **设备管理**: ESP32设备的注册、配置、状态监控\n" +
                                "- **语音交互**: STT、TTS、语音克隆\n" +
                                "- **AI对话**: 支持多种AI模型(OpenAI、智谱、讯飞等)\n" +
                                "- **固件管理**: OTA升级、版本管理\n" +
                                "- **会员与权限**: 会员体系、权限控制\n\n" +
                                "### 认证说明\n" +
                                "本API使用 Bearer Token 进行认证,大部分接口需要在请求头中携带token:\n" +
                                "```\n" +
                                "Authorization: Bearer your-token-here\n" +
                                "```")
                        .version("5.0.0")
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT"))
                        .contact(new Contact()
                                .name("Joey")
                                .email("1277676045@qq.com")
                                .url("https://github.com/joey-zhou/xiaozhi-esp32-server-java")))
                .externalDocs(new ExternalDocumentation()
                        .description("小智ESP32项目文档")
                        .url("https://github.com/joey-zhou/xiaozhi-esp32-server-java"))
                .servers(List.of(
                        new Server().url("http://localhost:8091").description("本地开发环境 - 后端"),
                        new Server().url("http://localhost:8084").description("本地开发环境 - 前端")))
                .components(new Components()
                        .addSecuritySchemes("Bearer Token", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Token")
                                .description("请输入登录后获取的 token (无需添加 Bearer 前缀，系统会自动添加)")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Token"));
    }
}
