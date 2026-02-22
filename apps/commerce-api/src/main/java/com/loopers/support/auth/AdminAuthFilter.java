package com.loopers.support.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!requiresAuthentication(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ldapValue = request.getHeader(HEADER_LDAP);

        if (!ADMIN_LDAP_VALUE.equals(ldapValue)) {
            sendUnauthorizedResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response) throws IOException {
        ErrorType errorType = ErrorType.UNAUTHORIZED;
        response.setStatus(errorType.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Object> body = ApiResponse.fail(errorType.getCode(), errorType.getMessage());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private boolean requiresAuthentication(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api-admin/");
    }
}
