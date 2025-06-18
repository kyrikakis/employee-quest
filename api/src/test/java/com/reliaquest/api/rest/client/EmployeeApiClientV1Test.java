package com.reliaquest.api.rest.client;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.reliaquest.api.exception.ExternalApiException;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.rest.client.model.MockEmployee;
import com.reliaquest.api.rest.client.model.MockResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
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
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    private WebClient.RequestBodySpec requestBodySpec;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

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
        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(WebClient.RequestBodySpec.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

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
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(getAllPath)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Server Error", null, null, null)));

        Flux<Employee> result = employeeApiClient.getAllEmployeesResponse();

        StepVerifier.create(result)
                .expectErrorSatisfies(throwable -> {
                    assertInstanceOf(ExternalApiException.class, throwable);
                    assertEquals(500, ((ExternalApiException) throwable).getStatus());
                })
                .verify();

        verify(webClient, times(1)).get();
        verify(requestHeadersUriSpec, times(1)).uri(getAllPath);
        verify(requestHeadersSpec, times(1)).retrieve();
        verify(responseSpec, times(1)).bodyToMono(any(ParameterizedTypeReference.class));
    }

    @Test
    void testCreateEmployee_success() {
        CreateEmployeeInput input = new CreateEmployeeInput();
        input.setName("Test User");
        input.setAge(30);
        input.setSalary(100000);
        input.setTitle("Engineer");

        MockEmployee mockEmployee = new MockEmployee();
        mockEmployee.setId("abc123");
        mockEmployee.setEmployeeName("Test User");
        mockEmployee.setEmployeeAge(30);
        mockEmployee.setEmployeeSalary(100000);
        mockEmployee.setEmployeeTitle("Engineer");
        mockEmployee.setEmployeeEmail("test@example.com");

        MockResponse<MockEmployee> mockResponse = new MockResponse<>(mockEmployee, "success");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(createPath)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(input)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(mockResponse));

        StepVerifier.create(employeeApiClient.createEmployee(input))
                .expectNextMatches(
                        emp -> emp.getName().equals("Test User") && emp.getId().equals("abc123"))
                .verifyComplete();
    }

    @Test
    void testCreateEmployee_ErrorResponse() {
        CreateEmployeeInput input = new CreateEmployeeInput();
        input.setName("Test User");
        input.setAge(30);
        input.setSalary(100000);
        input.setTitle("Engineer");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(createPath)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(input)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Server Error", null, null, null)));

        Mono<Employee> result = employeeApiClient.createEmployee(input);

        StepVerifier.create(result)
                .expectErrorSatisfies(throwable -> {
                    assertInstanceOf(ExternalApiException.class, throwable);
                    assertEquals(500, ((ExternalApiException) throwable).getStatus());
                })
                .verify();
    }

    @Test
    void testDeleteEmployeeByName_Success() {
        String name = "John Doe";
        MockResponse<Boolean> mockResponse = new MockResponse<>(true, "success");

        when(webClient.method(HttpMethod.DELETE)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(deletePath)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(Map.of("name", name))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(mockResponse));

        StepVerifier.create(employeeApiClient.deleteEmployeeByName(name))
                .expectNext(true)
                .verifyComplete();

        verify(webClient, times(1)).method(HttpMethod.DELETE);
        verify(requestBodyUriSpec, times(1)).uri(deletePath);
        verify(requestBodySpec, times(1)).bodyValue(Map.of("name", name));
        verify(requestHeadersSpec, times(1)).retrieve();
        verify(responseSpec, times(1)).bodyToMono(any(ParameterizedTypeReference.class));
    }

    @Test
    void testDeleteEmployeeByName_FailureFromApi() {
        String name = "Jane Smith";
        MockResponse<Boolean> mockResponse = new MockResponse<>(false, "not found");

        when(webClient.method(HttpMethod.DELETE)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(deletePath)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(Map.of("name", name))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(mockResponse));

        StepVerifier.create(employeeApiClient.deleteEmployeeByName(name))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testDeleteEmployeeByName_ErrorResponse() {
        String name = "Ghost";

        when(webClient.method(HttpMethod.DELETE)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(deletePath)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(Map.of("name", name))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Server Error", null, null, null)));

        StepVerifier.create(employeeApiClient.deleteEmployeeByName(name))
                .expectErrorSatisfies(throwable -> {
                    assertInstanceOf(ExternalApiException.class, throwable);
                    assertEquals(500, ((ExternalApiException) throwable).getStatus());
                })
                .verify();
    }
}
