package com.reliaquest.api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.lettucemod.RedisModulesClient;
import com.redis.lettucemod.api.StatefulRedisModulesConnection;
import com.redis.lettucemod.api.sync.RedisModulesCommands;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.rest.client.EmployeeApiClientV1;
import io.lettuce.core.RedisURI;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EmployeeServiceTest {

    // --- Mocks for EmployeeService Dependencies ---
    @Mock
    private EmployeeApiClientV1 employeeApiClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ReactiveRedisTemplate<String, Employee> reactiveEmployeeRedisTemplate;

    @Mock
    private ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;

    @Mock
    private RedisProperties redisProperties;

    // --- Mocks for ReactiveRedisTemplate's operations ---
    @Mock
    private ReactiveValueOperations<String, Employee> reactiveEmployeeValueOperations;

    @Mock
    private ReactiveZSetOperations<String, String> reactiveStringZSetOperations;

    // --- Mocks for RedisModulesClient (for @PostConstruct) ---
    private static MockedStatic<RedisModulesClient> mockedRedisModulesClient;
    private static RedisModulesClient mockRedisModulesClientInstance;
    private static StatefulRedisModulesConnection<String, String> mockRedisConnection;
    private static RedisModulesCommands<String, String> mockSyncCommands;

    // --- Service Under Test ---
    @InjectMocks
    private EmployeeService employeeService;

    // --- Static setup for RedisModulesClient mock ---
    @BeforeAll
    static void setupStaticMocks() {
        mockRedisModulesClientInstance = mock(RedisModulesClient.class);
        mockRedisConnection = mock(StatefulRedisModulesConnection.class);
        mockSyncCommands = mock(RedisModulesCommands.class);

        mockedRedisModulesClient = mockStatic(RedisModulesClient.class);
        when(RedisModulesClient.create(any(RedisURI.class))).thenReturn(mockRedisModulesClientInstance);

        when(mockRedisModulesClientInstance.connect()).thenReturn(mockRedisConnection);
        when(mockRedisConnection.sync()).thenReturn(mockSyncCommands);
    }

    @AfterAll
    static void tearDownStaticMocks() {
        if (mockedRedisModulesClient != null) {
            mockedRedisModulesClient.close();
        }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(reactiveEmployeeRedisTemplate.opsForValue()).thenReturn(reactiveEmployeeValueOperations);
        when(reactiveStringRedisTemplate.opsForZSet()).thenReturn(reactiveStringZSetOperations);

        // Manually initialize the EmployeeService with the mocked dependencies as
        // @PostContruct is racing @InjectMocks
        employeeService = new EmployeeService(
                employeeApiClient, reactiveEmployeeRedisTemplate, reactiveStringRedisTemplate, redisProperties);
    }

    @Test
    void testGetAllEmployees_withEmployeesInCache() {
        // Arrange
        Employee employee1 = new Employee("1", "John Doe", 50000, 50, "Mr", "john@doe.com");
        Employee employee2 = new Employee("2", "Jane Smith", 60000, 39, "Ms", "jane@smith.com");

        when(reactiveEmployeeRedisTemplate.keys(EmployeeService.EMPLOYEE_KEY_PREFIX + "*"))
                .thenReturn(Flux.just("employee:1", "employee:2"));

        when(reactiveEmployeeValueOperations.get("employee:1")).thenReturn(Mono.just(employee1));
        when(reactiveEmployeeValueOperations.get("employee:2")).thenReturn(Mono.just(employee2));

        // Act & Assert
        StepVerifier.create(employeeService.getAllEmployees())
                .expectNext(employee1)
                .expectNext(employee2)
                .expectComplete()
                .verify();

        // Verify interactions
        verify(reactiveEmployeeRedisTemplate, times(1)).keys(EmployeeService.EMPLOYEE_KEY_PREFIX + "*");
        verify(reactiveEmployeeValueOperations, times(1)).get("employee:1");
        verify(reactiveEmployeeValueOperations, times(1)).get("employee:2");
    }

    @Test
    void testGetAllEmployees_noEmployeesInCache() {
        // Arrange
        when(reactiveEmployeeRedisTemplate.keys(EmployeeService.EMPLOYEE_KEY_PREFIX + "*"))
                .thenReturn(Flux.empty());

        // Act & Assert
        StepVerifier.create(employeeService.getAllEmployees())
                .expectNextCount(0)
                .expectComplete()
                .verify();

        // Verify interactions
        verify(reactiveEmployeeRedisTemplate, times(1)).keys(EmployeeService.EMPLOYEE_KEY_PREFIX + "*");
        verify(reactiveEmployeeValueOperations, never()).get(anyString());
    }

    @Test
    void testRefreshAllEmployeesCache_withEmployees() {
        // Arrange
        Employee employee1 = new Employee("ref-1", "Refresh John", 50000, 30, "Mr", "ref.john@example.com");
        Employee employee2 = new Employee("ref-2", "Refresh Jane", 60000, 28, "Ms", "ref.jane@example.com");
        List<Employee> apiEmployees = Arrays.asList(employee1, employee2);

        when(employeeApiClient.getAllEmployeesResponse()).thenReturn(Flux.fromIterable(apiEmployees));

        when(reactiveEmployeeRedisTemplate.keys(anyString())).thenReturn(Flux.just("employee:old1", "employee:old2"));
        when(reactiveEmployeeRedisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(reactiveStringRedisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        when(reactiveEmployeeValueOperations.set(anyString(), any(Employee.class)))
                .thenReturn(Mono.just(true));
        when(reactiveStringZSetOperations.add(anyString(), anyString(), anyDouble()))
                .thenReturn(Mono.just(true));

        employeeService.refreshAllEmployeesCache();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(employeeApiClient, times(1)).getAllEmployeesResponse();
        verify(reactiveEmployeeRedisTemplate, times(1)).keys(EmployeeService.EMPLOYEE_KEY_PREFIX + "*");
        verify(reactiveEmployeeRedisTemplate, times(1))
                .delete(startsWith(EmployeeService.EMPLOYEE_KEY_PREFIX + "old1"));
        verify(reactiveEmployeeRedisTemplate, times(1))
                .delete(startsWith(EmployeeService.EMPLOYEE_KEY_PREFIX + "old2"));
        verify(reactiveStringRedisTemplate, times(1)).delete(anyString());

        verify(reactiveEmployeeValueOperations, times(1)).set(eq("employee:ref-1"), eq(employee1));
        verify(reactiveEmployeeValueOperations, times(1)).set(eq("employee:ref-2"), eq(employee2));
        verify(reactiveStringZSetOperations, times(1)).add(eq("employee_salaries"), eq("ref-1"), eq(50000.0));
        verify(reactiveStringZSetOperations, times(1)).add(eq("employee_salaries"), eq("ref-2"), eq(60000.0));
    }
}
