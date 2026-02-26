package com.loopers.support.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;

public class AuthUtils {

    private AuthUtils() {}

    public static String getAuthenticatedLoginId(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedLoginId");
        if (!(attribute instanceof String loginId)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증된 회원 정보가 없습니다.");
        }
        return loginId;
    }
}
