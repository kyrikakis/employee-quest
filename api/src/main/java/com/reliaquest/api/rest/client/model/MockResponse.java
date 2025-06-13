package com.reliaquest.api.rest.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class MockResponse<T> {

    private final T data;
    private final String status;
}
