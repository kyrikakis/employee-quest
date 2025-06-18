package com.reliaquest.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.lettucemod.api.StatefulRedisModulesConnection;
import com.redis.lettucemod.api.reactive.RedisModulesReactiveCommands;
import com.redis.lettucemod.search.CreateOptions;
import com.redis.lettucemod.search.Field;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.rest.client.EmployeeApiClientV1;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class EmployeeService {

    static final String EMPLOYEE_KEY_PREFIX = "employee:";
    private static final String REDISSEARCH_INDEX_NAME = "employeeIdx"; // Or "idx:employees" from your past code
    private static final String SALARY_ZSET_KEY = "employee_salaries"; // Or "employees:salary_zset"

    private final EmployeeApiClientV1 employeeApiClient;
    private final StatefulRedisModulesConnection<String, String> redisModulesConnection;
    private RedisModulesReactiveCommands<String, String> redisModulesReactiveCommands;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for EmployeeService.
     *
     * @param employeeApiClient           The client to fetch employee data from the
     *                                    external API.
     * @param redisModulesConnection      The Redis connection for modules (JSON,
     *                                    Search).
     * @param objectMapper                The Jackson ObjectMapper for JSON
     *                                    serialization/deserialization.
     */
    public EmployeeService(
            EmployeeApiClientV1 employeeApiClient,
            StatefulRedisModulesConnection<String, String> redisModulesConnection,
            ObjectMapper objectMapper) {
        this.employeeApiClient = employeeApiClient;
        this.redisModulesConnection = redisModulesConnection;
        this.objectMapper = objectMapper;

        redisModulesReactiveCommands = redisModulesConnection.reactive();
    }

    @PostConstruct
    @SuppressWarnings("unchecked") // Suppress unchecked warning for generic varargs in ftCreate
    public void initializeRedisCache() {
        log.info("Initializing Redis cache and indexes...");

        redisModulesReactiveCommands
                .ftCreate(
                        REDISSEARCH_INDEX_NAME,
                        CreateOptions.<String, String>builder()
                                .prefix(EMPLOYEE_KEY_PREFIX)
                                .on(CreateOptions.DataType.JSON)
                                .build(),
                        Field.tag("$.id").as("id").build(),
                        Field.text("$.name").as("name").noStem().sortable().build())
                .doOnSuccess(ok -> log.info("RedisSearch index '{}' created successfully.", REDISSEARCH_INDEX_NAME))
                .doOnError(e -> log.warn("RedisSearch index creation failed or already exists: {}", e.getMessage()))
                .then(Mono.fromRunnable(this::refreshAllEmployeesCache))
                .subscribe();

        refreshAllEmployeesCache();
    }

    @PreDestroy
    public void cleanup() {
        if (redisModulesConnection != null && redisModulesConnection.isOpen()) {
            log.info("Closing RedisModulesConnection.");
            // Lettuce connections have a close() method to release resources
            redisModulesConnection.close();
        }
    }

    /**
     * This method fetches all employees from the external API and repopulates the
     * Redis cache.
     * It acts as both the initial load (@PostConstruct calls it) and the scheduled
     * eviction/refresh.
     * All existing employee data in Redis is effectively replaced or updated.
     */
    @Scheduled(fixedRateString = "${app.cache.refresh-interval-ms:300000}") // Default to 5 minutes (300,000 ms)
    public void refreshAllEmployeesCache() {
        log.info(
                "Scheduled cache refresh: Fetching all employees from external API to refresh Redis cache and indexes.");

        employeeApiClient
                .getAllEmployeesResponse() // Returns Flux<Employee>
                .collectList()
                .flatMap(employees -> {
                    if (employees.isEmpty()) {
                        log.warn(
                                "No employees found from external API to refresh cache. Redis cache might remain stale.");
                        return Mono.empty();
                    }

                    // Delete all existing employee JSON keys and ZSET
                    log.info("Deleting existing employee keys and salary ZSET from Redis before refresh.");
                    Mono<Long> deleteKeysMono = redisModulesReactiveCommands
                            .keys(EMPLOYEE_KEY_PREFIX + "*")
                            .flatMap(redisModulesReactiveCommands::del)
                            .doOnError(
                                    e -> log.error("Failed to delete employee keys during refresh: {}", e.getMessage()))
                            .then(redisModulesReactiveCommands.del(SALARY_ZSET_KEY))
                            .doOnError(
                                    e -> log.error("Failed to delete salary ZSET during refresh: {}", e.getMessage()));

                    // Index new employees
                    List<Mono<Void>> indexOperations =
                            employees.stream().map(this::indexEmployeeInRedis).collect(Collectors.toList());

                    return deleteKeysMono
                            .then(Mono.when(indexOperations))
                            .then(Mono.fromRunnable(() -> log.info(
                                    "Successfully refreshed Redis cache with {} employees.", employees.size())));
                })
                .doOnError(e -> log.error("Failed to refresh Redis cache from external API: {}", e.getMessage()))
                .subscribe();
    }

    // --- Helper to add/update an employee in Redis (primary cache, salary ZSET,
    // RedisSearch) ---
    private Mono<Void> indexEmployeeInRedis(Employee employee) {
        String employeeKey = EMPLOYEE_KEY_PREFIX + employee.getId();

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(employee))
                .flatMap(json -> redisModulesReactiveCommands
                        .jsonSet(employeeKey, "$", json)
                        .doOnNext(res -> log.debug("Stored employee {} in Redis: {}", employee.getId(), res))
                        .then(redisModulesReactiveCommands
                                .zadd(SALARY_ZSET_KEY, employee.getSalary(), employee.getId())
                                .doOnError(e ->
                                        log.error("Failed ZSET insert for {}: {}", employee.getId(), e.getMessage()))
                                .then()));
    }

    // --- API Service Methods ---

    public Flux<Employee> getAllEmployees() {
        log.info("Retrieving all employees from Redis JSON store.");

        return redisModulesReactiveCommands
                .keys(EMPLOYEE_KEY_PREFIX + "*")
                .flatMap(key -> redisModulesReactiveCommands.jsonGet(key).flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, Employee.class));
                    } catch (Exception e) {
                        log.warn("Failed to deserialize employee JSON from {}: {}", key, e.getMessage());
                        return Mono.empty();
                    }
                }));
    }

    public Flux<Employee> getEmployeesByNameSearch(String nameFragment) {
        log.info("Searching employees by name fragment: '{}'", nameFragment);

        String query = "@name:(*" + nameFragment.toLowerCase() + "*)";

        return redisModulesReactiveCommands
                .ftSearch(REDISSEARCH_INDEX_NAME, query)
                .flatMapMany(results -> Flux.fromIterable(results))
                .map(doc -> doc.get("$")) // `$` is the full JSON field
                .flatMap(json -> {
                    try {
                        Employee employee = objectMapper.readValue(json, Employee.class);
                        return Mono.just(employee);
                    } catch (Exception e) {
                        log.warn("Failed to parse Employee JSON: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    public Mono<Employee> getEmployeeById(String id) {
        String key = EMPLOYEE_KEY_PREFIX + id;
        log.info("Fetching employee with ID: {}", id);

        return redisModulesReactiveCommands.jsonGet(key).flatMap(json -> {
            try {
                return Mono.just(objectMapper.readValue(json, Employee.class));
            } catch (Exception e) {
                log.warn("Failed to parse employee JSON for ID {}: {}", id, e.getMessage());
                return Mono.empty();
            }
        });
    }

    public Mono<Integer> getHighestSalaryOfEmployees() {
        return redisModulesReactiveCommands
                .zrevrangeWithScores(SALARY_ZSET_KEY, 0, 0) // Get top 1 highest score with ID
                .singleOrEmpty()
                .map(tuple -> (int) tuple.getScore());
    }

    public Flux<String> getTop10HighestEarningEmployeeNames() {
        return redisModulesReactiveCommands.zrevrange(SALARY_ZSET_KEY, 0, 9).flatMap(id -> getEmployeeById(id)
                .map(Employee::getName));
    }
}
