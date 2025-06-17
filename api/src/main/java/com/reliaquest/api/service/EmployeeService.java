package com.reliaquest.api.service;

import com.redis.lettucemod.RedisModulesClient;
import com.redis.lettucemod.api.sync.RedisModulesCommands;
import com.redis.lettucemod.search.CreateOptions;
import com.redis.lettucemod.search.Field;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.rest.client.EmployeeApiClientV1;
import io.lettuce.core.RedisURI;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private static final String REDISSEARCH_INDEX_NAME = "employeeIdx"; // Or "idx:employees" from your past code
    static final String EMPLOYEE_KEY_PREFIX = "employee:";
    private static final String SALARY_ZSET_KEY = "employee_salaries"; // Or "employees:salary_zset"

    private final EmployeeApiClientV1 employeeApiClient;
    private final ReactiveRedisTemplate<String, Employee> reactiveEmployeeRedisTemplate; // For Employee objects
    private final ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate; // For String values (ZSET members)
    private final RedisProperties redisProperties;

    private RedisModulesClient redisModulesClient;

    @PostConstruct
    @SuppressWarnings("unchecked") // Suppress unchecked warning for generic varargs in ftCreate
    public void initializeRedisCache() {
        log.info("Initializing Redis cache and indexes...");

        RedisURI redisURI = RedisURI.builder()
                .withHost(redisProperties.getHost())
                .withPort(redisProperties.getPort())
                .withTimeout(
                        redisProperties.getTimeout() != null ? redisProperties.getTimeout() : Duration.ofSeconds(30))
                .withDatabase(redisProperties.getDatabase())
                .withPassword(
                        redisProperties.getPassword() != null
                                ? redisProperties.getPassword().toCharArray()
                                : null)
                .build();
        redisModulesClient = RedisModulesClient.create(redisURI);

        try (var connection = redisModulesClient.connect()) {
            RedisModulesCommands<String, String> syncCommands = connection.sync();

            try {
                // Ensure index is created (or exists)
                syncCommands.ftCreate(
                        REDISSEARCH_INDEX_NAME,
                        CreateOptions.<String, String>builder()
                                .prefix(EMPLOYEE_KEY_PREFIX)
                                .on(CreateOptions.DataType.JSON)
                                .build(),
                        Field.tag("$.id").as("id").build(),
                        Field.text("$.name").as("name").build(),
                        Field.numeric("$.salary").as("salary").build());
                log.info("RedisSearch index '{}' created successfully.", REDISSEARCH_INDEX_NAME);
            } catch (Exception e) {
                log.warn(
                        "RedisSearch index '{}' might already exist or failed to create: {}",
                        REDISSEARCH_INDEX_NAME,
                        e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to connect to Redis during @PostConstruct initialization: {}", e.getMessage());
            throw new RuntimeException("Failed to connect to Redis for initialization: " + e.getMessage(), e);
        }

        // Trigger initial data load after RedisModulesClient is initialized
        refreshAllEmployeesCache();
    }

    @PreDestroy
    public void cleanup() {
        if (redisModulesClient != null) {
            log.info("Closing RedisModulesClient connection.");
            redisModulesClient.shutdown();
        }
    }

    /**
     * This method fetches all employees from the external API and repopulates the Redis cache.
     * It acts as both the initial load (@PostConstruct calls it) and the scheduled eviction/refresh.
     * All existing employee data in Redis is effectively replaced or updated.
     */
    @Scheduled(fixedRateString = "${app.cache.refresh-interval-ms:300000}") // Default to 5 minutes (300,000 ms)
    public void refreshAllEmployeesCache() {
        log.info(
                "Scheduled cache refresh: Fetching all employees from external API to refresh Redis cache and indexes.");

        employeeApiClient
                .getAllEmployeesResponse() // Returns Flux<Employee>
                .collectList() // Collect all employees into a single Mono<List<Employee>>
                .flatMap(employees -> {
                    if (employees.isEmpty()) {
                        log.warn(
                                "No employees found from external API to refresh cache. Redis cache might remain stale.");
                        return Mono.empty();
                    }

                    // Delete existing keys and ZSET before fresh load to avoid stale data
                    Mono<Long> deleteKeysMono = reactiveEmployeeRedisTemplate
                            .keys(EMPLOYEE_KEY_PREFIX + "*")
                            .flatMap(reactiveEmployeeRedisTemplate::delete)
                            .doOnError(e -> log.error(
                                    "Failed to delete existing employee keys during refresh: {}", e.getMessage()))
                            .then(reactiveStringRedisTemplate.delete(SALARY_ZSET_KEY))
                            .doOnError(
                                    e -> log.error("Failed to delete salary ZSET during refresh: {}", e.getMessage()));

                    List<Mono<Void>> indexOperations =
                            employees.stream().map(this::indexEmployeeInRedis).collect(Collectors.toList());

                    return deleteKeysMono.then(Mono.when(indexOperations)).then(Mono.fromCallable(() -> {
                        log.info("Successfully refreshed Redis cache with {} employees.", employees.size());
                        return null;
                    }));
                })
                .doOnError(e -> log.error("Failed to refresh Redis cache from external API: {}", e.getMessage()))
                .subscribe(); // Subscribe to trigger the reactive chain
    }

    // --- Helper to add/update an employee in Redis (primary cache, salary ZSET, RedisSearch) ---
    private Mono<Void> indexEmployeeInRedis(Employee employee) {
        String employeeKey = EMPLOYEE_KEY_PREFIX + employee.getId();
        log.debug("Indexing employee {} in Redis.", employee.getId());

        // 1. Add to Primary Redis Store (using reactiveEmployeeRedisTemplate for Employee objects)
        Mono<Boolean> primaryStoreMono = reactiveEmployeeRedisTemplate
                .opsForValue()
                .set(employeeKey, employee)
                .doOnError(e -> log.error(
                        "Failed to set employee {} in primary Redis store: {}", employee.getId(), e.getMessage()));

        // 2. Add to Salary Sorted Set (ZSET) (using reactiveStringRedisTemplate for String members)
        // Redis ZSETs always store scores as doubles. Relying on implicit widening from Integer/int to double.
        Mono<Boolean> salaryIndexMono = reactiveStringRedisTemplate
                .opsForZSet()
                .add(SALARY_ZSET_KEY, employee.getId(), employee.getSalary())
                .doOnError(e ->
                        log.error("Failed to add employee {} to salary ZSET: {}", employee.getId(), e.getMessage()));

        return Mono.when(primaryStoreMono, salaryIndexMono).then();
    }

    // --- API Service Methods ---

    public Flux<Employee> getAllEmployees() {
        log.info("Retrieving all employees from Redis primary store.");
        return reactiveEmployeeRedisTemplate
                .keys(EMPLOYEE_KEY_PREFIX + "*")
                .flatMap(key -> reactiveEmployeeRedisTemplate.opsForValue().get(key))
                .filter(Objects::nonNull);
    }
}
