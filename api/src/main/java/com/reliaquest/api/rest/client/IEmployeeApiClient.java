package com.reliaquest.api.rest.client;

import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IEmployeeApiClient {

    Flux<Employee> getAllEmployeesResponse();

    Mono<Employee> createEmployee(@RequestBody CreateEmployeeInput input);
}
