package com.loopers.infrastructure.payment.dto;

public record PgApiResponse<T>(
    Metadata meta,
    T data
) {
    public record Metadata(
        String result,
        String errorCode,
        String message
    ) {
        public boolean isSuccess() {
            return "SUCCESS".equals(result);
        }
    }
}
