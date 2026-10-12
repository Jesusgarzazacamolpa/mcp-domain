package com.floworchestrator.mcpdomain;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void advertisesToolsAndRejectsInvalidArgumentsOverStreamableHttp() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();
        McpSyncClient client = McpClient.sync(transport).build();

        try {
            client.initialize();
            Set<String> toolNames = client.listTools().tools().stream()
                    .map(McpSchema.Tool::name)
                    .collect(Collectors.toSet());

            assertEquals(Set.of(
                    "get_vehicle_information",
                    "register_vehicle_purchase",
                    "set_payment_method"), toolNames);
                McpSchema.Tool paymentTool = client.listTools().tools().stream()
                    .filter(tool -> "set_payment_method".equals(tool.name()))
                    .findFirst()
                    .orElseThrow();
                assertEquals(java.util.List.of("purchase_order_id"), paymentTool.inputSchema().get("required"));

            McpSchema.CallToolResult result = client.callTool(
                    McpSchema.CallToolRequest.builder("get_vehicle_information")
                            .arguments(Map.of())
                            .build());
            assertTrue(Boolean.TRUE.equals(result.isError()));
        } finally {
            client.closeGracefully();
        }
    }
}