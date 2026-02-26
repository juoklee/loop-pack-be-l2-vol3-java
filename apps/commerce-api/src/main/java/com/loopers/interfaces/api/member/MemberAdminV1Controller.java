package com.loopers.interfaces.api.member;

import com.loopers.application.PagedInfo;
import com.loopers.application.member.AdminMemberInfo;
import com.loopers.application.member.MemberFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/members")
public class MemberAdminV1Controller {

    private final MemberFacade memberFacade;

    @GetMapping
    public ApiResponse<MemberAdminV1Dto.MemberListResponse> getMembers(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PagedInfo<AdminMemberInfo> result = memberFacade.getMembersForAdmin(keyword, page, size);
        return ApiResponse.success(MemberAdminV1Dto.MemberListResponse.from(result));
    }

    @GetMapping("/{memberId}")
    public ApiResponse<MemberAdminV1Dto.MemberResponse> getMember(@PathVariable Long memberId) {
        AdminMemberInfo info = memberFacade.getMemberForAdmin(memberId);
        return ApiResponse.success(MemberAdminV1Dto.MemberResponse.from(info));
    }
}
