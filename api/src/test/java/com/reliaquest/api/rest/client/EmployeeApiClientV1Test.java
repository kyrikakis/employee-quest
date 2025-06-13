package com.reliaquest.api.rest.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.reliaquest.api.model.Employee;
import com.reliaquest.api.rest.client.model.MockEmployee;
import com.reliaquest.api.rest.client.model.MockResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
class EmployeeApiClientV1Test {

    private WebClient.Builder webClientBuilder;
    private WebClient webClient; // This will be the mock instance
    private EmployeeApiClientV1 employeeApiClient;

    // Directly assign values for test constants
    private final String baseUrl = "http://mock-api.com";
    private final String getAllPath = "/employees";
    private final String createPath = "/employees/create";
    private final String deletePath = "/employees/delete";

    @BeforeEach
    void setUp() {
        // Mock WebClient.Builder and WebClient instances
        webClientBuilder = mock(WebClient.Builder.class);
        webClient = mock(WebClient.class);

        // Chain the mocks for the WebClient creation process
        when(webClientBuilder.baseUrl(baseUrl)).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        // Instantiate the client with the mocked builder
        employeeApiClient = new EmployeeApiClientV1(baseUrl, getAllPath, createPath, deletePath, webClientBuilder);
    }

    @Test
    void testGetAllEmployeesResponse_Success() {
        MockEmployee mockEmployee1 = new MockEmployee("1", "John Doe", 50000, 30, "Developer", "john@doe.com");
        MockEmployee mockEmployee2 =
                new MockEmployee("2", "Jane Smith", 60000, 28, "Manager", "jane.smith@example.com");
        List<MockEmployee> mockEmployeeList = Arrays.asList(mockEmployee1, mockEmployee2);
        MockResponse<List<MockEmployee>> mockResponse = new MockResponse<>(mockEmployeeList, "success");

        // Use chained mocking for the WebClient fluent API.
        // Mock the sequence: webClient.get().uri(path).retrieve().bodyToMono(type)
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(getAllPath)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(mockResponse));

        Flux<Employee> result = employeeApiClient.getAllEmployeesResponse();

        StepVerifier.create(result)
                .expectNextMatches(employee -> "1".equals(employee.getId()) && "John Doe".equals(employee.getName()))
                .expectNextMatches(employee -> "2".equals(employee.getId()) && "Jane Smith".equals(employee.getName()))
                .verifyComplete();

        verify(webClient, times(1)).get();
        verify(requestHeadersUriSpec, times(1)).uri(getAllPath);
        verify(requestHeadersSpec, times(1)).retrieve();
        verify(responseSpec, times(1)).bodyToMono(any(ParameterizedTypeReference.class));
    }

    @Test
    void testGetAllEmployeesResponse_EmptyResponse() {
        MockResponse<List<Object>> mockResponse = new MockResponse<>(Collections.emptyList(), "success");

        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(getAllPath)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(mockResponse));

        Flux<Employee> result = employeeApiClient.getAllEmployeesResponse();

        StepVerifier.create(result).verifyComplete();

        verify(webClient, times(1)).get();
        verify(requestHeadersUriSpec, times(1)).uri(getAllPath);
        verify(requestHeadersSpec, times(1)).retrieve();
        verify(responseSpec, times(1)).bodyToMono(any(ParameterizedTypeReference.class));
    }

    @Test
    void testGetAllEmployeesResponse_ErrorResponse() {
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(getAllPath)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Server Error", null, null, null)));

        Flux<Employee> result = employeeApiClient.getAllEmployeesResponse();

        StepVerifier.create(result).verifyComplete();

        verify(webClient, times(1)).get();
        verify(requestHeadersUriSpec, times(1)).uri(getAllPath);
        verify(requestHeadersSpec, times(1)).retrieve();
        verify(responseSpec, times(1)).bodyToMono(any(ParameterizedTypeReference.class));
    }
}
