package com.reliaquest.api.controller;

import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/employee")
@RequiredArgsConstructor
public class EmployeeRestController implements IEmployeeController<Employee, Employee> {

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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEmployeesByNameSearch'");
    }

    @Override
    public ResponseEntity<Employee> getEmployeeById(String id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEmployeeById'");
    }

    @Override
    public ResponseEntity<Integer> getHighestSalaryOfEmployees() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getHighestSalaryOfEmployees'");
    }

    @Override
    public ResponseEntity<List<String>> getTopTenHighestEarningEmployeeNames() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTopTenHighestEarningEmployeeNames'");
    }

    @Override
    public ResponseEntity<Employee> createEmployee(Employee employeeInput) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createEmployee'");
    }

    @Override
    public ResponseEntity<String> deleteEmployeeById(String id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'deleteEmployeeById'");
    }
}
