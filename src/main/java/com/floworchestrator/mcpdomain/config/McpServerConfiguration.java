package com.floworchestrator.mcpdomain.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floworchestrator.mcpdomain.tools.RegisterVehiclePurchaseTool;
import com.floworchestrator.mcpdomain.tools.SetPaymentMethodTool;
import com.floworchestrator.mcpdomain.tools.VehicleInformationTool;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.Servlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class McpServerConfiguration {

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public ServletRegistrationBean<Servlet> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp");
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer mcpServer(
            HttpServletStreamableServerTransportProvider transportProvider,
            VehicleInformationTool vehicleInformationTool,
            RegisterVehiclePurchaseTool registerVehiclePurchaseTool,
            SetPaymentMethodTool setPaymentMethodTool,
            ObjectMapper objectMapper) {
        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("mcp-domain", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();
        server.addTool(getVehicleInformationTool(vehicleInformationTool, objectMapper));
        server.addTool(registerVehiclePurchaseTool(registerVehiclePurchaseTool, objectMapper));
        server.addTool(setPaymentMethodTool(setPaymentMethodTool, objectMapper));
        return server;
    }

    private SyncToolSpecification getVehicleInformationTool(
            VehicleInformationTool vehicleInformationTool,
            ObjectMapper objectMapper) {
        Map<String, Object> schema = objectSchema(
                Map.of(
                        "model_id", stringSchema("Vehicle model identifier"),
                        "agency_id", stringSchema("Agency identifier")),
                List.of("model_id", "agency_id"));
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("get_vehicle_information", schema)
                        .description("Gets vehicle availability and catalog details")
                        .build())
                .callHandler((exchange, request) -> result(objectMapper, vehicleInformationTool.execute(
                        stringArgument(request, "model_id"))))
                .build();
    }

    private SyncToolSpecification registerVehiclePurchaseTool(
            RegisterVehiclePurchaseTool registerVehiclePurchaseTool,
            ObjectMapper objectMapper) {
        Map<String, Object> schema = objectSchema(
                Map.of(
                        "customer_id", stringSchema("Customer identifier"),
                        "vehicle_id", stringSchema("Vehicle identifier"),
                        "sales_agent_id", stringSchema("Sales agent identifier")),
                List.of("customer_id", "vehicle_id", "sales_agent_id"));
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("register_vehicle_purchase", schema)
                        .description("Creates a purchase order for a customer and vehicle")
                        .build())
                .callHandler((exchange, request) -> result(objectMapper, registerVehiclePurchaseTool.execute(
                        stringArgument(request, "customer_id"),
                        stringArgument(request, "vehicle_id"),
                        stringArgument(request, "sales_agent_id"))))
                .build();
    }

    private SyncToolSpecification setPaymentMethodTool(
            SetPaymentMethodTool setPaymentMethodTool,
            ObjectMapper objectMapper) {
        Map<String, Object> schema = objectSchema(
                Map.of(
                        "purchase_order_id", stringSchema("Purchase order identifier"),
                        "payment_type", Map.of("type", "string", "description", "Payment type; defaults to FINANCING"),
                        "financial_institution", Map.of("type", "string", "description", "Defaults to BBVA"),
                        "down_payment_amount", Map.of("type", "number", "description", "Defaults to 15000 MXN")),
                List.of("purchase_order_id"));
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("set_payment_method", schema)
                        .description("Configures payment for a purchase. Defaults: FINANCING, BBVA, 15000 MXN.")
                        .build())
                .callHandler((exchange, request) -> result(objectMapper, setPaymentMethodTool.execute(
                        stringArgument(request, "purchase_order_id"),
                        optionalStringArgument(request, "payment_type", "FINANCING"),
                        optionalStringArgument(request, "financial_institution", "BBVA"),
                        optionalNumberArgument(request, "down_payment_amount", 15000))))
                .build();
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private String stringArgument(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (!(value instanceof String argument) || argument.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return argument;
    }

    private Number numberArgument(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (!(value instanceof Number argument) || argument.doubleValue() < 0) {
            throw new IllegalArgumentException("Invalid numeric argument: " + name);
        }
        return argument;
    }

        private String optionalStringArgument(McpSchema.CallToolRequest request, String name, String defaultValue) {
                Object value = request.arguments().get(name);
                if (value == null) {
                        return defaultValue;
                }
                if (!(value instanceof String argument) || argument.isBlank()) {
                        throw new IllegalArgumentException("Invalid string argument: " + name);
                }
                return argument;
        }

        private Number optionalNumberArgument(McpSchema.CallToolRequest request, String name, Number defaultValue) {
                return request.arguments().containsKey(name) ? numberArgument(request, name) : defaultValue;
        }

    private McpSchema.CallToolResult result(ObjectMapper objectMapper, Map<String, Object> value) {
        try {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(objectMapper.writeValueAsString(value))))
                    .build();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize tool result", ex);
        }
    }
}