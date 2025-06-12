package com.reliaquest.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.reliaquest.api.model.Employee;
import com.reliaquest.api.rest.client.EmployeeApiClientService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiApplicationTestIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeApiClientService employeeApiClientService;

    @Test
    void shouldReturnAllEmployees() throws Exception {
        List<Employee> mockEmployees = List.of(
                new Employee("1", "John Doe", 50000, 50, "Mr"), new Employee("2", "Jane Smith", 60000, 30, "Ms"));

        when(employeeApiClientService.getAllEmployeesResponse()).thenReturn(mockEmployees);

        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[1].name").value("Jane Smith"));
        ;
    }
}
