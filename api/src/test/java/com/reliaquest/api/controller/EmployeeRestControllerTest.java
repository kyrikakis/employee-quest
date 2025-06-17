package com.reliaquest.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

public class EmployeeRestControllerTest {

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private EmployeeRestController employeeRestController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllEmployees() {
        // Arrange
        Employee employee1 = new Employee("1", "John Doe", 50000, 50, "Mr", "joe@doe.com");
        Employee employee2 = new Employee("2", "Jane Smith", 60000, 30, "Ms", "jane@smith.com");
        List<Employee> mockEmployees = Arrays.asList(employee1, employee2);

        when(employeeService.getAllEmployees()).thenReturn(Flux.fromIterable(mockEmployees));

        // Act
        ResponseEntity<List<Employee>> response = employeeRestController.getAllEmployees();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockEmployees, response.getBody());
        verify(employeeService, times(1)).getAllEmployees();
    }
}
