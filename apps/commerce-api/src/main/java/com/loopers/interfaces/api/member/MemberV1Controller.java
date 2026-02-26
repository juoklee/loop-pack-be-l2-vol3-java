package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller {

    private final MemberFacade memberFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberV1Dto.MemberResponse> register(
        @RequestBody MemberV1Dto.RegisterRequest request
    ) {
        MemberInfo info = memberFacade.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.gender(),
            request.email(),
            request.phone()
        );
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(info));
    }

    @GetMapping("/me")
    public ApiResponse<MemberV1Dto.MemberResponse> getMe(HttpServletRequest request) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        MemberInfo info = memberFacade.getMe(loginId);
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
        HttpServletRequest request,
        @RequestBody MemberV1Dto.ChangePasswordRequest passwordRequest
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        memberFacade.changePassword(
            loginId,
            passwordRequest.currentPassword(),
            passwordRequest.newPassword()
        );
        return ApiResponse.success(null);
    }

    @PatchMapping("/me")
    public ApiResponse<Void> updatePhone(
        HttpServletRequest request,
        @RequestBody MemberV1Dto.UpdatePhoneRequest phoneRequest
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        memberFacade.updatePhone(loginId, phoneRequest.phone());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(
        HttpServletRequest request,
        @RequestBody MemberV1Dto.WithdrawRequest withdrawRequest
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        memberFacade.withdraw(loginId, withdrawRequest.password());
        return ApiResponse.success(null);
    }
}
