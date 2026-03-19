package com.loopers.infrastructure.payment;

import com.loopers.infrastructure.payment.dto.PgApiResponse;
import com.loopers.infrastructure.payment.dto.PgOrderResponse;
import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.infrastructure.payment.dto.PgTransactionDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PgPaymentClient {

    private static final String HEADER_USER_ID = "X-USER-ID";
    private static final String PAYMENTS_PATH = "/api/v1/payments";

    private final RestTemplate pgRestTemplate;

    public PgPaymentClient(@Qualifier("pgRestTemplate") RestTemplate pgRestTemplate) {
        this.pgRestTemplate = pgRestTemplate;
    }

    public PgPaymentResponse requestPayment(Long memberId, PgPaymentRequest request) {
        HttpHeaders headers = createHeaders(memberId);
        HttpEntity<PgPaymentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<PgApiResponse<PgPaymentResponse>> response = pgRestTemplate.exchange(
            PAYMENTS_PATH,
            HttpMethod.POST,
            entity,
            new ParameterizedTypeReference<>() {}
        );

        return response.getBody().data();
    }

    public PgTransactionDetailResponse getTransaction(Long memberId, String transactionKey) {
        HttpHeaders headers = createHeaders(memberId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<PgApiResponse<PgTransactionDetailResponse>> response = pgRestTemplate.exchange(
            PAYMENTS_PATH + "/{transactionKey}",
            HttpMethod.GET,
            entity,
            new ParameterizedTypeReference<>() {},
            transactionKey
        );

        return response.getBody().data();
    }

    public PgOrderResponse getTransactionsByOrderId(Long memberId, String orderId) {
        HttpHeaders headers = createHeaders(memberId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<PgApiResponse<PgOrderResponse>> response = pgRestTemplate.exchange(
            PAYMENTS_PATH + "?orderId={orderId}",
            HttpMethod.GET,
            entity,
            new ParameterizedTypeReference<>() {},
            orderId
        );

        return response.getBody().data();
    }

    private HttpHeaders createHeaders(Long memberId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, String.valueOf(memberId));
        return headers;
    }
}
