package com.reliaquest.api.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.lettucemod.api.StatefulRedisModulesConnection;
import com.redis.lettucemod.api.reactive.RedisModulesReactiveCommands;
import com.redis.lettucemod.search.Document;
import com.redis.lettucemod.search.SearchResults;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.rest.client.EmployeeApiClientV1;
import io.lettuce.core.ScoredValue;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EmployeeServiceTest {

    @Mock
    private EmployeeApiClientV1 employeeApiClient;

    @Mock
    private StatefulRedisModulesConnection<String, String> redisModulesConnection;

    @Mock
    private RedisModulesReactiveCommands<String, String> redisModulesReactiveCommands;

    @InjectMocks
    private EmployeeService employeeService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisModulesConnection.reactive()).thenReturn(redisModulesReactiveCommands);
        employeeService = new EmployeeService(employeeApiClient, redisModulesConnection, objectMapper);
    }

    @Test
    void testGetAllEmployees_withResults() throws Exception {
        Employee emp1 = new Employee("1", "Alice", 50000, 30, "Developer", "alice@example.com");
        Employee emp2 = new Employee("2", "Bob", 60000, 35, "Manager", "bob@example.com");

        String json1 = objectMapper.writeValueAsString(emp1);
        String json2 = objectMapper.writeValueAsString(emp2);

        when(redisModulesReactiveCommands.keys("employee:*"))
                .thenReturn(Flux.fromIterable(List.of("employee:1", "employee:2")));
        when(redisModulesReactiveCommands.jsonGet("employee:1")).thenReturn(Mono.just(json1));
        when(redisModulesReactiveCommands.jsonGet("employee:2")).thenReturn(Mono.just(json2));

        StepVerifier.create(employeeService.getAllEmployees())
                .expectNextMatches(e -> e.getId().equals("1"))
                .expectNextMatches(e -> e.getId().equals("2"))
                .verifyComplete();
    }

    @Test
    void testGetEmployeesByNameSearch_withResults() throws Exception {
        // Arrange
        Employee emp = new Employee("1", "Alice", 50000, 30, "Developer", "alice@example.com");
        String json = objectMapper.writeValueAsString(emp);

        Document<String, String> doc = Document.<String, String>id("employee:1")
                .score(1.0)
                .field("$", json)
                .build();
        SearchResults<String, String> results = new SearchResults<>();
        results.add(doc);
        results.setCount(1);

        when(redisModulesReactiveCommands.ftSearch(eq("employeeIdx"), anyString()))
                .thenReturn(Mono.just(results));

        // Act & Assert
        StepVerifier.create(employeeService.getEmployeesByNameSearch("ali"))
                .expectNextMatches(e -> e.getName().equalsIgnoreCase("Alice"))
                .verifyComplete();
    }

    @Test
    void testRefreshAllEmployeesCache_withEmployees() {
        // Given
        Employee employee1 = new Employee("id-1", "Alice", 100000, 30, "Engineer", "alice@example.com");
        Employee employee2 = new Employee("id-2", "Bob", 90000, 40, "Manager", "bob@example.com");
        List<Employee> employees = List.of(employee1, employee2);

        when(employeeApiClient.getAllEmployeesResponse()).thenReturn(Flux.fromIterable(employees));

        // Simulate existing keys to delete
        when(redisModulesReactiveCommands.keys("employee:*")).thenReturn(Flux.just("employee:old1", "employee:old2"));
        when(redisModulesReactiveCommands.del("employee:old1")).thenReturn(Mono.just(1L));
        when(redisModulesReactiveCommands.del("employee:old2")).thenReturn(Mono.just(1L));
        when(redisModulesReactiveCommands.del("employee_salaries")).thenReturn(Mono.just(1L));

        // Simulate storing and indexing
        when(redisModulesReactiveCommands.jsonSet(eq("employee:id-1"), eq("$"), contains("Alice")))
                .thenReturn(Mono.just("OK"));
        when(redisModulesReactiveCommands.jsonSet(eq("employee:id-2"), eq("$"), contains("Bob")))
                .thenReturn(Mono.just("OK"));

        when(redisModulesReactiveCommands.zadd("employee_salaries", 100000.0, "id-1"))
                .thenReturn(Mono.just(1L));
        when(redisModulesReactiveCommands.zadd("employee_salaries", 90000.0, "id-2"))
                .thenReturn(Mono.just(1L));

        // When
        employeeService.refreshAllEmployeesCache();

        // Then
        verify(redisModulesReactiveCommands).keys("employee:*");
        verify(redisModulesReactiveCommands).del("employee:old1");
        verify(redisModulesReactiveCommands).del("employee:old2");
        verify(redisModulesReactiveCommands).del("employee_salaries");

        verify(redisModulesReactiveCommands).jsonSet(eq("employee:id-1"), eq("$"), contains("Alice"));
        verify(redisModulesReactiveCommands).jsonSet(eq("employee:id-2"), eq("$"), contains("Bob"));

        verify(redisModulesReactiveCommands).zadd("employee_salaries", 100000.0, "id-1");
        verify(redisModulesReactiveCommands).zadd("employee_salaries", 90000.0, "id-2");
    }

    @Test
    void testGetEmployeeById_found() throws Exception {
        // Given
        String employeeId = "id-1";
        String redisKey = "employee:" + employeeId;

        Employee expectedEmployee = new Employee(employeeId, "Alice", 100000, 30, "Engineer", "alice@example.com");
        String json = objectMapper.writeValueAsString(expectedEmployee);

        when(redisModulesReactiveCommands.jsonGet(redisKey)).thenReturn(Mono.just(json));

        // When
        StepVerifier.create(employeeService.getEmployeeById(employeeId))
                .expectNext(expectedEmployee)
                .verifyComplete();

        // Then
        verify(redisModulesReactiveCommands).jsonGet(redisKey);
    }

    @Test
    void testGetHighestSalaryOfEmployees_returnsHighest() {
        String highestPaidId = "id-999";
        double highestSalary = 200000.0;

        when(redisModulesReactiveCommands.zrevrangeWithScores(eq("employee_salaries"), eq(0L), eq(0L)))
                .thenReturn(Flux.just(ScoredValue.just(highestSalary, highestPaidId)));

        StepVerifier.create(employeeService.getHighestSalaryOfEmployees())
                .expectNext((int) highestSalary)
                .verifyComplete();
    }

    @Test
    void testGetTop10HighestEarningEmployeeNames_limitedToTop10() throws JsonProcessingException {
        // Create 12 employees with decreasing salaries
        List<Employee> employees = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> new Employee(
                        "id-" + i,
                        "Employee-" + i,
                        200000 - i * 1000, // Decreasing salary
                        30 + i,
                        "Role-" + i,
                        "employee" + i + "@example.com"))
                .collect(Collectors.toList());

        // Extract top 10 IDs and names
        List<String> top10Ids =
                employees.subList(0, 10).stream().map(e -> e.getId()).collect(Collectors.toList());

        List<String> expectedNames =
                employees.subList(0, 10).stream().map(Employee::getName).collect(Collectors.toList());

        // Mock zrevrange to return top 10 IDs
        when(redisModulesReactiveCommands.zrevrange(eq("employee_salaries"), eq(0L), eq(9L)))
                .thenReturn(Flux.fromIterable(top10Ids));

        // Mock JSON lookups for each employee
        for (Employee emp : employees.subList(0, 10)) {
            when(redisModulesReactiveCommands.jsonGet("employee:" + emp.getId()))
                    .thenReturn(Mono.just(objectMapper.writeValueAsString(emp)));
        }

        // Verify
        StepVerifier.create(employeeService.getTop10HighestEarningEmployeeNames())
                .expectNextSequence(expectedNames)
                .verifyComplete();
    }
}
