package com.reliaquest.api.controller;

import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/v1/employee")
@RequiredArgsConstructor
public class EmployeeRestController implements IEmployeeController<Employee, CreateEmployeeInput> {

    @Getter
    private final EmployeeService employeeService;

    @Override
    public ResponseEntity<List<Employee>> getAllEmployees() {
        List<Employee> allEmployees =
                employeeService.getAllEmployees().collectList().block();
        return ResponseEntity.ok().body(allEmployees);
    }

    @Override
    public ResponseEntity<List<Employee>> getEmployeesByNameSearch(String searchString) {
        List<Employee> allEmployees = employeeService
                .getEmployeesByNameSearch(searchString)
                .collectList()
                .block();
        return ResponseEntity.ok().body(allEmployees);
    }

    @Override
    public ResponseEntity<Employee> getEmployeeById(String id) {
        Mono<Employee> employeeMono = employeeService.getEmployeeById(id);
        Employee employee = employeeMono.block();
        if (employee != null) {
            return ResponseEntity.ok().body(employee);
        }
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<Integer> getHighestSalaryOfEmployees() {
        Integer highestSalary = employeeService.getHighestSalaryOfEmployees().block();
        if (highestSalary != null) {
            return ResponseEntity.ok().body(highestSalary);
        }
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<List<String>> getTopTenHighestEarningEmployeeNames() {
        List<String> topTenNames = employeeService
                .getTop10HighestEarningEmployeeNames()
                .collectList()
                .block();
        if (topTenNames != null && !topTenNames.isEmpty()) {
            return ResponseEntity.ok().body(topTenNames);
        }
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<Employee> createEmployee(@Valid CreateEmployeeInput employeeInput) {
        return employeeService
                .createEmployee(employeeInput)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .defaultIfEmpty(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
                .block();
    }

    @Override
    public ResponseEntity<String> deleteEmployeeById(String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }
        Mono<String> deleteResult = employeeService.deleteEmployeeById(id);
        return deleteResult
                .map(deletedId -> ResponseEntity.ok().body(deletedId))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .block();
    }
}
