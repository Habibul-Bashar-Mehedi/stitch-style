package com.stitchstyle.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.media.local-root:./data/media}")
    private String localRoot;

    @org.springframework.context.annotation.Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(localRoot).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/media/**").addResourceLocations(location);
    }
}
