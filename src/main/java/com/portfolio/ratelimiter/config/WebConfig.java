package com.portfolio.ratelimiter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Vite React dev server (localhost:5173) to call the dashboard
 * endpoints during development. Scoped to /api/dashboard/** only - the rest of
 * the API is not CORS-open. In Phase 6 the frontend is served from the same
 * origin, so this is a dev-time convenience.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/dashboard/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                .allowedMethods("GET");
    }
}