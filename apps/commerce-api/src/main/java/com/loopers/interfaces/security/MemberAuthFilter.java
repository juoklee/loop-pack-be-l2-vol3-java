package com.loopers.interfaces.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.member.MemberFacade;
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
public class MemberAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final MemberFacade memberFacade;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 인증이 필요 없는 경로는 통과
        if (!requiresAuthentication(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String loginPw = request.getHeader(HEADER_LOGIN_PW);

        // 헤더가 없으면 401
        if (loginId == null || loginPw == null) {
            sendUnauthorizedResponse(response);
            return;
        }

        // 회원 조회 및 비밀번호 검증
        boolean authenticated = memberFacade.authenticate(loginId, loginPw);

        if (!authenticated) {
            sendUnauthorizedResponse(response);
            return;
        }

        // 인증 성공 - loginId를 request에 저장
        request.setAttribute("authenticatedLoginId", loginId);
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
        String path = request.getRequestURI();
        String method = request.getMethod();

        // POST /api/v1/members (회원가입)는 인증 불필요
        if ("POST".equals(method) && "/api/v1/members".equals(path)) {
            return false;
        }

        // /api/v1/members/** 경로는 인증 필요
        if (path.startsWith("/api/v1/members/")) {
            return true;
        }

        // POST /api/v1/products/{id}/likes, POST /api/v1/brands/{id}/likes 인증 필요
        if ("POST".equals(method) && path.endsWith("/likes")
            && (path.startsWith("/api/v1/products/") || path.startsWith("/api/v1/brands/"))) {
            return true;
        }

        // /api/v1/orders/** 경로는 인증 필요
        if (path.startsWith("/api/v1/orders")) {
            return true;
        }

        // /api/v1/payments/** 경로는 인증 필요
        if (path.startsWith("/api/v1/payments")) {
            return true;
        }

        // POST /api/v1/coupons/{couponId}/issue 인증 필요
        if ("POST".equals(method) && path.startsWith("/api/v1/coupons/") && path.endsWith("/issue")) {
            return true;
        }

        // /api/v1/users/me/** 경로는 인증 필요
        if (path.startsWith("/api/v1/users/me")) {
            return true;
        }

        return false;
    }
}
