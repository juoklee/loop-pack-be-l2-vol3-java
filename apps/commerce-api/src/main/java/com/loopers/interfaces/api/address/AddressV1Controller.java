package com.loopers.interfaces.api.address;

import com.loopers.application.address.AddressFacade;
import com.loopers.application.address.AddressInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members/me/addresses")
public class AddressV1Controller {

    private final AddressFacade addressFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddressV1Dto.AddressResponse> register(
        HttpServletRequest request,
        @RequestBody AddressV1Dto.CreateAddressRequest body
    ) {
        String loginId = getAuthenticatedLoginId(request);
        AddressInfo info = addressFacade.register(
            loginId, body.label(), body.recipientName(), body.recipientPhone(),
            body.zipCode(), body.address1(), body.address2()
        );
        return ApiResponse.success(AddressV1Dto.AddressResponse.from(info));
    }

    @GetMapping
    public ApiResponse<AddressV1Dto.AddressListResponse> getAddresses(HttpServletRequest request) {
        String loginId = getAuthenticatedLoginId(request);
        List<AddressInfo> infos = addressFacade.getAddresses(loginId);
        return ApiResponse.success(AddressV1Dto.AddressListResponse.from(infos));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<Void> update(
        HttpServletRequest request,
        @PathVariable Long addressId,
        @RequestBody AddressV1Dto.UpdateAddressRequest body
    ) {
        String loginId = getAuthenticatedLoginId(request);
        addressFacade.update(
            loginId, addressId, body.label(), body.recipientName(), body.recipientPhone(),
            body.zipCode(), body.address1(), body.address2()
        );
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> delete(
        HttpServletRequest request,
        @PathVariable Long addressId
    ) {
        String loginId = getAuthenticatedLoginId(request);
        addressFacade.delete(loginId, addressId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{addressId}/default")
    public ApiResponse<Void> setDefault(
        HttpServletRequest request,
        @PathVariable Long addressId
    ) {
        String loginId = getAuthenticatedLoginId(request);
        addressFacade.setDefault(loginId, addressId);
        return ApiResponse.success(null);
    }

    private String getAuthenticatedLoginId(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedLoginId");
        if (!(attribute instanceof String loginId)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증된 회원 정보가 없습니다.");
        }
        return loginId;
    }
}
