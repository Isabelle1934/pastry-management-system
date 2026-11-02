package br.com.confeitaria.config;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.confeitaria.security.PasswordChangeRequiredInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor;

    public WebConfig(PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor) {
        this.passwordChangeRequiredInterceptor = passwordChangeRequiredInterceptor;
    }

    /** Usado pelo Spring Security ({@code http.cors}) — vale para toda a API, inclusive o Actuator. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        var source = new UrlBasedCorsConfigurationSource();
        var origins = properties.cors().allowedOrigins();
        if (origins.isEmpty()) {
            return source;
        }
        var config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passwordChangeRequiredInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/**");
    }
}
