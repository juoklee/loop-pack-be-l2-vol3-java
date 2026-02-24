package com.loopers.application;

import java.util.List;

public record PagedInfo<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {}
