package com.floworchestrator.mcpdomain;

import com.floworchestrator.mcpdomain.config.McpServerConfiguration;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void advertisesToolsResourcesAndActionableErrorsOverStreamableHttp() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();
        McpSyncClient client = McpClient.sync(transport).build();

        try {
            client.initialize();

            assertThat(client.getServerInstructions()).contains("idempotente");

            List<McpSchema.Tool> tools = client.listTools().tools();
            Set<String> toolNames = tools.stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
            assertThat(toolNames).containsExactlyInAnyOrder(
                    "get_vehicle_information", "register_vehicle_purchase", "set_payment_method",
                    "request_financing_approval");

            McpSchema.Tool vehicleTool = toolNamed(tools, "get_vehicle_information");
            assertThat(vehicleTool.inputSchema().get("required")).isEqualTo(List.of("vehicle_id"));

            McpSchema.Tool purchaseTool = toolNamed(tools, "register_vehicle_purchase");
            assertThat(propertiesOf(purchaseTool)).contains("idempotency_key");
            assertThat(purchaseTool.description()).contains("idempotente");

            McpSchema.Tool paymentTool = toolNamed(tools, "set_payment_method");
            assertThat(paymentTool.inputSchema().get("required")).isEqualTo(List.of("purchase_order_id"));

            McpSchema.Tool financingTool = toolNamed(tools, "request_financing_approval");
            assertThat(financingTool.inputSchema().get("required")).isEqualTo(List.of("purchase_order_id"));
            assertThat(propertiesOf(financingTool)).contains("decision");

            List<McpSchema.Resource> resources = client.listResources().resources();
            assertThat(resources).extracting(McpSchema.Resource::uri)
                    .contains(McpServerConfiguration.PAYMENT_DEFAULTS_URI);
            McpSchema.Resource paymentDefaults = resources.stream()
                    .filter(resource -> McpServerConfiguration.PAYMENT_DEFAULTS_URI.equals(resource.uri()))
                    .findFirst()
                    .orElseThrow();
            String resourceText = ((McpSchema.TextResourceContents)
                    client.readResource(paymentDefaults).contents().get(0)).text();
            assertThat(resourceText).contains("FINANCING").contains("BBVA").contains("15000");

            McpSchema.CallToolResult result = client.callTool(
                    McpSchema.CallToolRequest.builder("get_vehicle_information")
                            .arguments(Map.of())
                            .build());
            assertThat(result.isError()).isTrue();
            String errorText = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(errorText)
                    .contains("\"error_code\":\"MISSING_ARGUMENT\"")
                    .contains("\"how_to_fix\"")
                    .contains("el identificador del vehículo");
        } finally {
            client.close();
        }
    }

    private static McpSchema.Tool toolNamed(List<McpSchema.Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> propertiesOf(McpSchema.Tool tool) {
        Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");
        return properties.keySet();
    }
}
