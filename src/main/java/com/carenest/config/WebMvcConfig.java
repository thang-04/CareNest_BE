package com.carenest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Prefix API chỉ gắn cho controller trong {@code com.carenest.controller} để không ảnh hưởng
 * springdoc/actuator; controller không được hardcode prefix.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String CONTROLLER_PACKAGE = "com.carenest.controller";

    private final String apiPrefix;

    public WebMvcConfig(@Value("${carenest.api.prefix}") String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(apiPrefix, HandlerTypePredicate.forBasePackage(CONTROLLER_PACKAGE));
    }
}
