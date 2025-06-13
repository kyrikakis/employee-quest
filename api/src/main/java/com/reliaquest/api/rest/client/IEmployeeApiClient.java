package com.reliaquest.api.rest.client;

import com.reliaquest.api.model.Employee;
import reactor.core.publisher.Flux;

public interface IEmployeeApiClient {

    Flux<Employee> getAllEmployeesResponse();
}
