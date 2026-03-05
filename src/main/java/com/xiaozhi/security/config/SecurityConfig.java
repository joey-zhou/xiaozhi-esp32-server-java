package com.xiaozhi.security.config;

import com.xiaozhi.security.AnonymousAccessManager;
import com.xiaozhi.security.CustomUserDetailsService;
import com.xiaozhi.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置类
 *
 * @author Joey
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AnonymousAccessManager anonymousAccessManager;

    /**
     * 配置安全过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（使用 JWT 不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 禁用 Session（使用 JWT 无状态认证）
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置 CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 配置授权规则
            .authorizeHttpRequests(auth -> auth
                // 静态资源和公开路径
                .requestMatchers(
                    "/",
                    "/favicon.ico",
                    "/error",
                    "/actuator/**",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-resources/**",
                    "/doc.html"
                ).permitAll()
                // 静态资源
                .requestMatchers(
                    "/assets/**",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/static/**",
                    "/public/**"
                ).permitAll()
                // 匿名访问接口（动态扫描）
                .requestMatchers(getAnonymousPaths()).permitAll()
                // OPTIONS 预检请求放行
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 其他所有请求需要认证
                .anyRequest().authenticated()
            )
            // 配置认证提供者
            .authenticationProvider(authenticationProvider())
            // 添加 JWT 认证过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // 禁用默认登录页面
            .formLogin(AbstractHttpConfigurer::disable)
            // 禁用 HTTP Basic
            .httpBasic(AbstractHttpConfigurer::disable);
        
        return http.build();
    }

    /**
     * 获取匿名访问路径数组
     */
    private String[] getAnonymousPaths() {
        // 固定的匿名路径
        List<String> fixedPaths = Arrays.asList(
            "/api/user/login",
            "/api/user/register",
            "/api/user/sendCode",
            "/api/user/resetPassword",
            "/api/user/wx-login",
            "/api/device/verifyCode",
            "/api/device/bind",
            "/api/chat/**",
            "/ws/**"
        );
        
        // 合并动态扫描的匿名路径
        List<String> allPaths = new java.util.ArrayList<>(fixedPaths);
        allPaths.addAll(anonymousAccessManager.getAnonymousPaths());
        
        return allPaths.toArray(new String[0]);
    }

    /**
     * 配置认证提供者
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * 配置认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 配置密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置 CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许所有源（生产环境应该指定具体域名）
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Access-Token",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        ));
        configuration.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "X-Access-Token"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
