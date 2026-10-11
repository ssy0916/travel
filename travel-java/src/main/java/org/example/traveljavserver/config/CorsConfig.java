package org.example.traveljavserver.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * 全局跨域配置。
 * 使用过滤器在最外层统一处理 CORS（含 OPTIONS 预检），普通接口和 SSE 流式接口都生效。
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许所有来源（带 Cookie 时不能用 "*"，需用 originPatterns）
        config.setAllowedOriginPatterns(List.of("*"));
        // 允许的 HTTP 方法，OPTIONS 预检必须放行
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // 允许所有请求头（含 Authorization、Content-Type 等自定义头）
        config.setAllowedHeaders(List.of("*"));
        // 允许前端读取的响应头
        config.setExposedHeaders(List.of("Content-Type", "Cache-Control"));
        // 允许携带 Cookie / 凭证
        config.setAllowCredentials(true);
        // 预检结果缓存 1 小时，减少 OPTIONS 请求
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        // 最高优先级，保证预检请求在业务过滤器之前被处理
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
