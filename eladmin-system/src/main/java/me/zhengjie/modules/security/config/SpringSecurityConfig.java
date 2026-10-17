/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.security.config;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.security.security.*;
import me.zhengjie.modules.security.service.OnlineUserService;
import me.zhengjie.utils.AnonTagUtils;
import me.zhengjie.utils.enums.RequestMethodEnum;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.CorsFilter;
import java.util.*;

/**
 * @author Zheng Jie
 */
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(securedEnabled = true)
public class SpringSecurityConfig {

    private final TokenProvider tokenProvider;
    private final CorsFilter corsFilter;
    private final JwtAuthenticationEntryPoint authenticationErrorHandler;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final ApplicationContext applicationContext;
    private final SecurityProperties properties;
    private final OnlineUserService onlineUserService;

    @Bean
    static GrantedAuthorityDefaults grantedAuthorityDefaults() {
        // 去除 ROLE_ 前缀
        return new GrantedAuthorityDefaults("");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 密码加密方式
        return new BCryptPasswordEncoder();
    }

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // 获取匿名标记
        Map<String, Set<String>> anonymousUrls = AnonTagUtils.getAnonymousUrl(applicationContext);
        return httpSecurity
                // 禁用 CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .addFilter(corsFilter)
                // 授权异常
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationErrorHandler)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                // 防止iframe 造成跨域
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                // 不创建会话
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 与 Spring Security 5 的 authorizeRequests 保持一致：不对 ERROR/FORWARD 转发再次鉴权
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        // 静态资源等等
                        .requestMatchers(antMatchers(HttpMethod.GET,
                                "/*.html",
                                "/**/*.html",
                                "/**/*.css",
                                "/**/*.js",
                                "/webSocket/**"
                        )).permitAll()
                        // swagger 文档 (springdoc-openapi + knife4j)
                        .requestMatchers(antMatchers(null,
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/*/api-docs",
                                "/v3/api-docs/**"
                        )).permitAll()
                        // 文件
                        .requestMatchers(antMatchers(null, "/avatar/**", "/file/**")).permitAll()
                        // 阿里巴巴 druid
                        .requestMatchers(antMatchers(null, "/druid/**")).permitAll()
                        // 放行OPTIONS请求
                        .requestMatchers(antMatchers(HttpMethod.OPTIONS, "/**")).permitAll()
                        // 自定义匿名访问所有url放行：允许匿名和带Token访问，细腻化到每个 Request 类型
                        // GET
                        .requestMatchers(antMatchers(HttpMethod.GET, anonymousUrls.get(RequestMethodEnum.GET.getType()))).permitAll()
                        // POST
                        .requestMatchers(antMatchers(HttpMethod.POST, anonymousUrls.get(RequestMethodEnum.POST.getType()))).permitAll()
                        // PUT
                        .requestMatchers(antMatchers(HttpMethod.PUT, anonymousUrls.get(RequestMethodEnum.PUT.getType()))).permitAll()
                        // PATCH
                        .requestMatchers(antMatchers(HttpMethod.PATCH, anonymousUrls.get(RequestMethodEnum.PATCH.getType()))).permitAll()
                        // DELETE
                        .requestMatchers(antMatchers(HttpMethod.DELETE, anonymousUrls.get(RequestMethodEnum.DELETE.getType()))).permitAll()
                        // 所有类型的接口都放行
                        .requestMatchers(antMatchers(null, anonymousUrls.get(RequestMethodEnum.ALL.getType()))).permitAll()
                        // 所有请求都需要认证
                        .anyRequest().authenticated())
                .with(securityConfigurerAdapter(), Customizer.withDefaults())
                .build();
    }

    private TokenConfigurer securityConfigurerAdapter() {
        return new TokenConfigurer(tokenProvider, properties, onlineUserService);
    }

    private static RequestMatcher[] antMatchers(HttpMethod method, Collection<String> patterns) {
        return antMatchers(method, patterns.toArray(new String[0]));
    }

    /**
     * 显式使用 Ant 风格匹配，保持与 Spring Security 5 antMatchers 相同的语义
     * （项目中注册了 Druid StatViewServlet，存在多个 Servlet 时 requestMatchers(String...) 无法自动选择匹配器）
     */
    @SuppressWarnings("deprecation")
    private static RequestMatcher[] antMatchers(HttpMethod method, String... patterns) {
        return Arrays.stream(patterns)
                .map(pattern -> method == null
                        ? AntPathRequestMatcher.antMatcher(pattern)
                        : AntPathRequestMatcher.antMatcher(method, pattern))
                .toArray(RequestMatcher[]::new);
    }
}
