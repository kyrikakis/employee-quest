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
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.reliaquest.api.rest.client.model.MockEmployee;
import com.reliaquest.api.rest.client.model.MockResponse;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
class ApiApplicationTestIT {

    // NEW: Stop WireMock server after all tests
    @AfterAll
    static void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    // NEW: WireMock server instance
    private static WireMockServer wireMockServer;
    // NEW: Define a fixed port for WireMock in tests
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

        registry.add("mock-employee-api.base-url", () -> "http://localhost:" + WIREMOCK_FIXED_TEST_PORT + "/employees");
        System.out.printf("🧪 Redis is at %s:%d%n", host, port);
    }

    // NEW: Start WireMock server before all tests
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
                        urlEqualTo("/employees")) // Matches GET requests to /employees
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(mockResponseBody)));
    }

    @Test
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
    void shouldReturnEmployeeByNameSearch() throws Exception {
        mockMvc.perform(get("/api/v1/employee/search/WireMock"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("WireMock Alice"));
    }

    @Test
    void shouldReturnEmployeeById() throws Exception {
        mockMvc.perform(get("/api/v1/employee/wiremock-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("wiremock-1"))
                .andExpect(jsonPath("$.name").value("WireMock Alice"));
    }

    @Test
    void shouldReturnHighestSalary() throws Exception {
        mockMvc.perform(get("/api/v1/employee/highestSalary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("80000"));
    }

    @Test
    void shouldReturnTop10HighestEarningEmployeeNames() throws Exception {
        mockMvc.perform(get("/api/v1/employee/topTenHighestEarningEmployeeNames"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("WireMock Bob"))
                .andExpect(jsonPath("$[1]").value("WireMock Alice"));
    }
}
