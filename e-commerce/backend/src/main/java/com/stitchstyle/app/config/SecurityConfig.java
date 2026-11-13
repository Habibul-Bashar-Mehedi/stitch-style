package com.stitchstyle.app.config;

import com.stitchstyle.app.security.CustomOidcUserService;
import com.stitchstyle.app.security.RestAccessDeniedHandler;
import com.stitchstyle.app.security.RestAuthenticationEntryPoint;
import com.stitchstyle.app.security.ReturnUrlAuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            CustomOidcUserService oidcUserService,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler,
                                            CsrfTokenRequestAttributeHandler csrfTokenRequestHandler,
                                            ReturnUrlAuthenticationSuccessHandler successHandler) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler)
                        .ignoringRequestMatchers("/api/v1/public/payments/*/callback", "/api/v1/dev/**"))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/public/**", "/oauth2/**", "/login/oauth2/**",
                                "/actuator/health/**",
                                "/media/**", "/sitemap.xml", "/robots.txt", "/api/v1/dev/**").permitAll()
                        // API documentation is back-office only: an ADMIN session is required
                        // for both the Swagger UI assets and the generated document.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("ADMIN")
                        .requestMatchers("/api/v1/auth/logout", "/api/v1/me/**", "/ws/**").authenticated()
                        .requestMatchers("/api/v1/admin/session").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/v1/admin/staff/**", "/api/v1/admin/settings/**",
                                "/api/v1/admin/delivery-areas/**", "/api/v1/admin/audit/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/analytics/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "MANAGER")
                        .anyRequest().denyAll())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(info -> info.oidcUserService(oidcUserService))
                        .successHandler(successHandler))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
                        .invalidateHttpSession(true)
                        .deleteCookies("STITCHSTYLE_SESSION", "XSRF-TOKEN"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200", "http://127.0.0.1:4200", "http://localhost:4000", "http://127.0.0.1:4000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Request-Id", "Idempotency-Key", "Accept-Language"));
        config.setExposedHeaders(List.of("X-Request-Id", "Idempotency-Replayed", "Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
