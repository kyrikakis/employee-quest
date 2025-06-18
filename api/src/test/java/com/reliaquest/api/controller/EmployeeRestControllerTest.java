package com.reliaquest.api.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliaquest.api.exception.ExternalApiException;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebMvcTest(EmployeeRestController.class)
class EmployeeRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService employeeService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetAllEmployees() throws Exception {
        Employee employee1 = new Employee("1", "John Doe", 50000, 50, "Mr", "joe@doe.com");
        Employee employee2 = new Employee("2", "Jane Smith", 60000, 30, "Ms", "jane@smith.com");
        List<Employee> mockEmployees = Arrays.asList(employee1, employee2);

        when(employeeService.getAllEmployees()).thenReturn(Flux.fromIterable(mockEmployees));

        mockMvc.perform(get("/api/v1/employee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[1].name").value("Jane Smith"));
    }

    @Test
    void testGetEmployeeById() throws Exception {
        String id = "1";
        Employee employee = new Employee(id, "John Doe", 50000, 50, "Mr", "joe@doe.com");

        when(employeeService.getEmployeeById(id)).thenReturn(Mono.just(employee));

        mockMvc.perform(get("/api/v1/employee/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void testGetEmployeesByName() throws Exception {
        String name = "Jane";
        Employee employee = new Employee("2", "Jane Smith", 60000, 30, "Ms", "jane@smith.com");
        List<Employee> matchingEmployees = Collections.singletonList(employee);

        when(employeeService.getEmployeesByNameSearch(name)).thenReturn(Flux.fromIterable(matchingEmployees));

        mockMvc.perform(get("/api/v1/employee/search/" + name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Jane Smith"));
    }

    @Test
    void testGetHighestSalaryOfEmployees() throws Exception {
        int highestSalary = 120000;

        when(employeeService.getHighestSalaryOfEmployees()).thenReturn(Mono.just(highestSalary));

        mockMvc.perform(get("/api/v1/employee/highestSalary"))
                .andExpect(status().isOk())
                .andExpect(content().string("120000"));
    }

    @Test
    void testGetTopTenHighestEarningEmployeeNames() throws Exception {
        List<String> names = List.of("Alice", "Bob");

        when(employeeService.getTop10HighestEarningEmployeeNames()).thenReturn(Flux.fromIterable(names));

        mockMvc.perform(get("/api/v1/employee/topTenHighestEarningEmployeeNames"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Alice"))
                .andExpect(jsonPath("$[1]").value("Bob"));
    }

    @Test
    void testCreateEmployee_success() throws Exception {
        CreateEmployeeInput input = new CreateEmployeeInput();
        input.setName("Alice");
        input.setAge(28);
        input.setSalary(85000);
        input.setTitle("Developer");

        Employee expectedEmployee = new Employee("emp-123", "Alice", 85000, 28, "Developer", "alice@example.com");

        when(employeeService.createEmployee(eq(input))).thenReturn(Mono.just(expectedEmployee));

        mockMvc.perform(post("/api/v1/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("emp-123"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void testCreateEmployee_invalid_age_returnsBadRequest() throws Exception {
        CreateEmployeeInput invalidInput = new CreateEmployeeInput();
        invalidInput.setName("Joe");
        invalidInput.setAge(10);
        invalidInput.setSalary(100000);
        invalidInput.setTitle("Manager");

        mockMvc.perform(post("/api/v1/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidInput)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.age").value("must be greater than or equal to 16"));
    }

    @Test
    void testCreateEmployee_invalid_name_returnsBadRequest() throws Exception {
        CreateEmployeeInput invalidInput = new CreateEmployeeInput();
        invalidInput.setName("");
        invalidInput.setAge(16);
        invalidInput.setSalary(100000);
        invalidInput.setTitle("Manager");

        mockMvc.perform(post("/api/v1/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidInput)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("must not be blank"));
    }

    @Test
    void testCreateEmployee_invalid_salary_returnsBadRequest() throws Exception {
        CreateEmployeeInput invalidInput = new CreateEmployeeInput();
        invalidInput.setName("Joe");
        invalidInput.setAge(16);
        invalidInput.setSalary(-1);
        invalidInput.setTitle("Manager");

        mockMvc.perform(post("/api/v1/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidInput)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.salary").value("must be greater than 0"));
    }

    @Test
    void testCreateEmployee_invalid_title_returnsBadRequest() throws Exception {
        CreateEmployeeInput invalidInput = new CreateEmployeeInput();
        invalidInput.setName("Joe");
        invalidInput.setAge(16);
        invalidInput.setSalary(100000);
        invalidInput.setTitle("");

        mockMvc.perform(post("/api/v1/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidInput)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("must not be blank"));
    }

    @Test
    void testCreateEmployee_ExternalApiFailure_ReturnsBadGateway() throws Exception {
        CreateEmployeeInput input = new CreateEmployeeInput();
        input.setName("Joe");
        input.setAge(16);
        input.setSalary(100000);
        input.setTitle("Manager");

        when(employeeService.createEmployee(eq(input))).thenReturn(Mono.error(new ExternalApiException("fail", 502)));

        mockMvc.perform(post("/api/v1/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error")
                        .value("The external service is currently unavailable. Please try again later."));
    }

    @Test
    void testGetAllEmployees_ExternalApiFailure_ReturnsBadGateway() throws Exception {
        when(employeeService.getAllEmployees()).thenReturn(Flux.error(new ExternalApiException("fail", 502)));

        mockMvc.perform(get("/api/v1/employee"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error")
                        .value("The external service is currently unavailable. Please try again later."));
    }
}
