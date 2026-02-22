package com.loopers.support.config;

import com.loopers.support.auth.AdminAuthFilter;
import com.loopers.support.auth.MemberAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final MemberAuthFilter memberAuthFilter;
    private final AdminAuthFilter adminAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(adminAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(memberAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
