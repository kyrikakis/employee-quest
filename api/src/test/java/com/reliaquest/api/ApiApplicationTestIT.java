package com.reliaquest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.rest.client.model.MockEmployee;
import com.reliaquest.api.rest.client.model.MockResponse;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiApplicationTestIT {

    @Autowired
    private MockMvc mockMvc;
    private static WireMockServer wireMockServer;
    private static final int WIREMOCK_FIXED_TEST_PORT = 8050;

    private static ObjectMapper objectMapper = new ObjectMapper();

    @Container
    public static GenericContainer<?> redisStackContainer =
            new GenericContainer<>(DockerImageName.parse("redis/redis-stack:latest")).withExposedPorts(6379);

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        assertTrue(redisStackContainer.isRunning(), "Redis Testcontainer should be running");
        String host = redisStackContainer.getHost();
        Integer port = redisStackContainer.getMappedPort(6379);
        registry.add("spring.redis.host", () -> host);
        registry.add("spring.redis.port", () -> port);

        registry.add("mock-employee-api.base-url", () -> "http://localhost:" + WIREMOCK_FIXED_TEST_PORT + "/employee");
        System.out.printf("🧪 Redis is at %s:%d%n", host, port);
    }

    @BeforeAll
    static void setupWireMock() throws JsonProcessingException {
        assertTrue(redisStackContainer.isRunning(), "Redis Testcontainer should be running");
        wireMockServer =
                new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_FIXED_TEST_PORT));
        wireMockServer.start();
        configureFor("localhost", WIREMOCK_FIXED_TEST_PORT); // Configure WireMock client to connect to it

        String mockResponseBody = objectMapper.writeValueAsString(new MockResponse<>(
                Arrays.asList(
                        new MockEmployee("wiremock-1", "WireMock Alice", 70000, 35, "Dev Lead", "walice@example.com"),
                        new MockEmployee("wiremock-2", "WireMock Bob", 80000, 40, "Architect", "wbob@example.com")),
                "success"));

        // Define the WireMock stub (mock response)
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                        urlEqualTo("/employee")) // Matches GET requests to /employees
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(mockResponseBody)));
    }

    @AfterAll
    static void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @Order(1)
    void shouldReturnAllEmployeesFromWireMock() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/employee"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2)) // Ensure correct total count
                .andExpect(jsonPath("$[?(@.id == 'wiremock-1')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'wiremock-1')].name").value("WireMock Alice"))
                .andExpect(jsonPath("$[?(@.id == 'wiremock-2')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'wiremock-2')].name").value("WireMock Bob"));
    }

    @Test
    @Order(2)
    void shouldReturnEmployeeByNameSearch() throws Exception {
        mockMvc.perform(get("/api/v1/employee/search/WireMock"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("WireMock Alice"));
    }

    @Test
    @Order(3)
    void shouldReturnEmployeeById() throws Exception {
        mockMvc.perform(get("/api/v1/employee/wiremock-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("wiremock-1"))
                .andExpect(jsonPath("$.name").value("WireMock Alice"));
    }

    @Test
    @Order(4)
    void shouldReturnHighestSalary() throws Exception {
        mockMvc.perform(get("/api/v1/employee/highestSalary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("80000"));
    }

    @Test
    @Order(5)
    void shouldReturnTop10HighestEarningEmployeeNames() throws Exception {
        mockMvc.perform(get("/api/v1/employee/topTenHighestEarningEmployeeNames"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("WireMock Bob"))
                .andExpect(jsonPath("$[1]").value("WireMock Alice"));
    }

    @Test
    @Order(6)
    void shouldCreateEmployeeSuccessfully() throws Exception {
        // Create mock request & expected mock response
        MockEmployee createdMockEmployee =
                new MockEmployee("wiremock-3", "New Guy", 75000, 32, "Engineer", "new.guy@example.com");

        MockResponse<MockEmployee> mockResponse = new MockResponse<>(createdMockEmployee, "success");

        // Stub the WireMock endpoint for POST
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/employee"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SC_CREATED)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(mockResponse))));

        // Prepare request JSON
        CreateEmployeeInput createEmployeeInput = new CreateEmployeeInput();
        createEmployeeInput.setName("New Guy");
        createEmployeeInput.setSalary(75000);
        createEmployeeInput.setAge(32);
        createEmployeeInput.setTitle("Engineer");

        String requestJson = objectMapper.writeValueAsString(createEmployeeInput);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("wiremock-3"))
                .andExpect(jsonPath("$.name").value("New Guy"))
                .andExpect(jsonPath("$.salary").value(75000))
                .andExpect(jsonPath("$.age").value(32))
                .andExpect(jsonPath("$.title").value("Engineer"))
                .andExpect(jsonPath("$.email").value("new.guy@example.com"));
    }

    @Test
    @Order(7)
    void shouldDeleteEmployeeSuccessfullyUsingApi() throws Exception {
        // Arrange
        String id = "wiremock-2";
        String name = "WireMock Bob";

        // Stub downstream API to allow deletion by name
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.delete(urlEqualTo("/employee"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing(name))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SC_OK)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(new MockResponse<>(true, "success")))));

        // Act: delete via your app's API
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/employee/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(name));

        // Assert: subsequent GET by ID should return 404
        mockMvc.perform(get("/api/v1/employee/" + id)).andExpect(status().isNotFound());
    }
}
