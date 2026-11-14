package com.nexora.os.config;

import com.nexora.os.auth.AppUserRepository;
import com.nexora.os.auth.NexoraPrincipal;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> users.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                .map(NexoraPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                           SecurityContextRepository contexts,
                                           AppUserRepository users,
                                           @Value("${nexora.security.legacy-basic-enabled:false}") boolean legacyBasicEnabled) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> {
                    String method = request.getMethod();
                    boolean unsafe = !("GET".equals(method) || "HEAD".equals(method)
                            || "OPTIONS".equals(method) || "TRACE".equals(method));
                    // Only explicitly enabled legacy test clients may bypass CSRF.
                    // Production defaults to requiring CSRF on all unsafe methods.
                    String authorization = request.getHeader("Authorization");
                    boolean legacyBasic = authorization != null
                            && authorization.regionMatches(true, 0, "Basic ", 0, 6);
                    return unsafe && !(legacyBasicEnabled && legacyBasic);
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(contexts))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/session/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/session/login").permitAll()
                        .requestMatchers("/api/team", "/api/team/**", "/api/security/events").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/leads", "/api/tasks").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/leads/**", "/api/tasks/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new SessionFreshnessFilter(users), AuthorizationFilter.class);
        if (legacyBasicEnabled) {
            http.addFilterBefore(new LoginAttemptFilter(), BasicAuthenticationFilter.class)
                    .httpBasic(Customizer.withDefaults());
        } else {
            http.httpBasic(basic -> basic.disable());
        }
        return http.build();
    }
}
