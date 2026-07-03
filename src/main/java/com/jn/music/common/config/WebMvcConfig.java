package com.jn.music.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.DELETE.name())
                .allowCredentials(true);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 核心：无尾斜杠入口统一重定向，避免相对静态资源被浏览器解析到 /music/assets。
        registry.addViewController("/admin").setViewName("redirect:/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
        registry.addViewController("/admin/{path:[^\\.]*}").setViewName("forward:/admin/index.html");
    }

    @Bean
    public FilterRegistrationBean<TraceIdConfig> traceIdFilter() {
        FilterRegistrationBean<TraceIdConfig> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdConfig());
        registration.addUrlPatterns("/*");
        return registration;
    }
}
