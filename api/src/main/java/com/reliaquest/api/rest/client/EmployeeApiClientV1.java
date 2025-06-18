package com.reliaquest.api.rest.client;

import com.reliaquest.api.exception.ExternalApiException;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.rest.client.model.MockEmployee;
import com.reliaquest.api.rest.client.model.MockResponse;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component("employeeApiClient")
@Slf4j
public class EmployeeApiClientV1 implements IEmployeeApiClient {

    private final WebClient webClient;

    // Inject all paths
    private final String getAllPath;
    private final String createPath;
    private final String deletePath;

    public EmployeeApiClientV1(
            @Value("${mock-employee-api.base-url}") String baseUrl,
            @Value("${mock-employee-api.get-all-path}") String getAllPath,
            @Value("${mock-employee-api.create-path}") String createPath,
            @Value("${mock-employee-api.delete-path}") String deletePath,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.getAllPath = getAllPath;
        this.createPath = createPath;
        this.deletePath = deletePath;
        log.info("EmployeeApiClientV1 initialized with base URL: {}", baseUrl);
        log.info("Paths initialized: getAllPath={}, createPath={}, deletePath={}", getAllPath, createPath, deletePath);
    }

    private Employee mapClientEmployeeToEmployee(MockEmployee clientEmployee) {
        if (clientEmployee == null) {
            return null;
        }
        // Assuming Employee has a constructor matching these fields
        // Or use a builder pattern/setter if that's your model's design
        return new Employee(
                clientEmployee.getId(),
                clientEmployee.getEmployeeName(),
                clientEmployee.getEmployeeSalary(),
                clientEmployee.getEmployeeAge(),
                clientEmployee.getEmployeeTitle(),
                clientEmployee.getEmployeeEmail());
    }

    public Flux<Employee> getAllEmployeesResponse() {
        log.info("Fetching all employees from path: {}", getAllPath);

        return webClient
                .get()
                .uri(getAllPath)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<MockResponse<List<MockEmployee>>>() {})
                // Explicitly specify the output type of flatMapMany: <Employee>
                .<Employee>flatMapMany(mockResponse -> {
                    log.debug("MockResponse status: {}", mockResponse.getStatus());
                    List<MockEmployee> clientEmployees =
                            Objects.requireNonNullElse(mockResponse.getData(), Collections.emptyList());
                    log.debug("Number of employees fetched: {}", clientEmployees.size());
                    if (clientEmployees.isEmpty()) {
                        log.warn("No employees found in the response.");
                    } else {
                        log.info("Mapping {} client employees to Employee objects.", clientEmployees.size());
                    }

                    return Flux.fromIterable(clientEmployees).map(this::mapClientEmployeeToEmployee);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Downstream API error: {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
                    return Mono.error(new ExternalApiException(
                            "Employee API error", e.getStatusCode().value()));
                })
                .doOnError(e -> log.error("Error fetching all employees: {}", e.getMessage()));
    }

    public Mono<Employee> createEmployee(CreateEmployeeInput input) {
        log.info("Creating employee via path: {}", createPath);

        return webClient
                .post()
                .uri(createPath)
                .bodyValue(input)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<MockResponse<MockEmployee>>() {})
                .<Employee>map(resp -> {
                    MockEmployee mockEmployee = resp.getData();
                    return mapClientEmployeeToEmployee(mockEmployee);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Downstream API error: {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
                    return Mono.error(new ExternalApiException(
                            "Employee API error", e.getStatusCode().value()));
                });
    }
}
