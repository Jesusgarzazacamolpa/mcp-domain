package com.floworchestrator.mcpdomain.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floworchestrator.mcpdomain.service.PaymentDefaults;
import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import com.floworchestrator.mcpdomain.tools.RegisterVehiclePurchaseTool;
import com.floworchestrator.mcpdomain.tools.SetPaymentMethodTool;
import com.floworchestrator.mcpdomain.tools.ToolResponse;
import com.floworchestrator.mcpdomain.tools.VehicleInformationTool;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServerConfigurationTest {

    private final McpServerConfiguration configuration = new McpServerConfiguration();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void vehicleInformationSpecIsStrictAndNarrative() {
        VehicleInformationTool tool = mock(VehicleInformationTool.class);
        when(tool.execute(Map.of("vehicle_id", "v-101")))
                .thenReturn(ToolResponse.success(Map.of("vehicle_id", "v-101", "brand", "Toyota")));

        SyncToolSpecification specification = configuration.vehicleInformationTool(tool, objectMapper);

        assertThat(specification.tool().name()).isEqualTo("get_vehicle_information");
        assertThat(specification.tool().description())
                .contains("solo lectura")
                .contains("VEHICLE_NOT_FOUND")
                .hasSizeGreaterThan(200);
        assertThat(specification.tool().annotations().readOnlyHint()).isTrue();
        assertThat(specification.tool().inputSchema())
                .containsEntry("required", List.of("vehicle_id"))
                .containsEntry("additionalProperties", false);
        assertThat(propertiesOf(specification.tool()))
                .containsOnlyKeys("vehicle_id");
        assertThat(propertyOf(specification.tool(), "vehicle_id"))
                .containsKeys("type", "description", "minLength", "maxLength", "examples");

        McpSchema.CallToolResult result = invoke(specification, Map.of("vehicle_id", "v-101"));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).contains("\"brand\":\"Toyota\"");
        verify(tool).execute(Map.of("vehicle_id", "v-101"));
    }

    @Test
    void toolErrorsAreReturnedAsActionableJson() {
        VehicleInformationTool tool = mock(VehicleInformationTool.class);
        when(tool.execute(anyMap())).thenReturn(ToolResponse.error(
                "get_vehicle_information", "MISSING_ARGUMENT",
                "Falta el argumento obligatorio 'vehicle_id'.",
                "Solicita al usuario el identificador del vehículo.", "vehicle_id", false));

        McpSchema.CallToolResult result = invoke(
                configuration.vehicleInformationTool(tool, objectMapper), Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(textOf(result))
                .contains("\"error_code\":\"MISSING_ARGUMENT\"")
                .contains("\"how_to_fix\":\"Solicita al usuario el identificador del vehículo.");
    }

    @Test
    void handlerContainsUnexpectedExceptions() {
        VehicleInformationTool tool = mock(VehicleInformationTool.class);
        when(tool.execute(anyMap())).thenThrow(new IllegalStateException("boom"));

        McpSchema.CallToolResult result = invoke(
                configuration.vehicleInformationTool(tool, objectMapper), Map.of("vehicle_id", "v-1"));

        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).contains("\"error_code\":\"INTERNAL_ERROR\"");
    }

    @Test
    void handlerReturnsSafeResponseWhenResultCannotBeSerialized() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(mock(JsonProcessingException.class));
        VehicleInformationTool tool = mock(VehicleInformationTool.class);
        when(tool.execute(anyMap())).thenReturn(ToolResponse.success(Map.of("value", "ok")));

        McpSchema.CallToolResult result = invoke(
                configuration.vehicleInformationTool(tool, failingMapper), Map.of("vehicle_id", "v-1"));

        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).contains("\"error_code\":\"SERIALIZATION_ERROR\"");
    }

    @Test
    void registerPurchaseSchemaRequiresBusinessIdentifiersAndDocumentsIdempotency() {
        RegisterVehiclePurchaseTool tool = mock(RegisterVehiclePurchaseTool.class);
        Map<String, Object> arguments = Map.of(
                "customer_id", "c-1", "vehicle_id", "v-1", "sales_agent_id", "a-1");
        when(tool.execute(arguments)).thenReturn(ToolResponse.success(Map.of("purchase_order_id", "po-1")));

        SyncToolSpecification specification = configuration.registerVehiclePurchaseTool(tool, objectMapper);

        assertThat(specification.tool().description())
                .contains("idempotente")
                .contains("idempotent_replay");
        assertThat(specification.tool().inputSchema().get("required"))
                .isEqualTo(List.of("customer_id", "vehicle_id", "sales_agent_id"));
        assertThat(propertiesOf(specification.tool()))
                .containsOnlyKeys("customer_id", "vehicle_id", "sales_agent_id", "idempotency_key");
        assertThat(propertyOf(specification.tool(), "idempotency_key").get("description").toString())
                .contains("Reutiliza exactamente el mismo valor");

        McpSchema.CallToolResult result = invoke(specification, arguments);

        assertThat(result.isError()).isFalse();
        verify(tool).execute(arguments);
    }

    @Test
    void paymentMethodSchemaPublishesDefaults() {
        SetPaymentMethodTool tool = mock(SetPaymentMethodTool.class);
        when(tool.execute(Map.of("purchase_order_id", "po-1")))
                .thenReturn(ToolResponse.success(Map.of("purchase_order_id", "po-1")));

        SyncToolSpecification specification = configuration.setPaymentMethodTool(tool, objectMapper);

        assertThat(specification.tool().inputSchema().get("required"))
                .isEqualTo(List.of("purchase_order_id"));
        assertThat(propertyOf(specification.tool(), "payment_type"))
                .containsEntry("default", PaymentDefaults.PAYMENT_TYPE);
        assertThat(propertyOf(specification.tool(), "financial_institution"))
                .containsEntry("default", PaymentDefaults.FINANCIAL_INSTITUTION);
        assertThat(propertyOf(specification.tool(), "down_payment_amount"))
                .containsEntry("default", PaymentDefaults.DOWN_PAYMENT_AMOUNT)
                .containsEntry("minimum", 0);

        McpSchema.CallToolResult result = invoke(specification, Map.of("purchase_order_id", "po-1"));

        assertThat(result.isError()).isFalse();
    }

    @Test
    void paymentDefaultsResourceExposesDefaultsAsJson() {
        SyncResourceSpecification specification = configuration.paymentDefaultsResource(objectMapper);

        assertThat(specification.resource().uri()).isEqualTo(McpServerConfiguration.PAYMENT_DEFAULTS_URI);
        assertThat(specification.resource().mimeType()).isEqualTo("application/json");

        McpSchema.ReadResourceResult result = specification.readHandler().apply(null, null);
        McpSchema.TextResourceContents contents =
                (McpSchema.TextResourceContents) result.contents().get(0);

        assertThat(contents.text())
                .contains("\"payment_type\":\"" + PaymentDefaults.PAYMENT_TYPE + "\"")
                .contains("\"financial_institution\":\"" + PaymentDefaults.FINANCIAL_INSTITUTION + "\"")
                .contains("\"down_payment_amount\":" + PaymentDefaults.DOWN_PAYMENT_AMOUNT);
    }

    @Test
    void resourceSerializationFailureReturnsSafePayload() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(mock(JsonProcessingException.class));

        McpSchema.ReadResourceResult result = configuration.paymentDefaultsResource(failingMapper)
                .readHandler().apply(null, null);

        assertThat(((McpSchema.TextResourceContents) result.contents().get(0)).text())
                .contains("SERIALIZATION_ERROR");
    }

    @Test
    void serverBeanExposesToolsResourcesAndInstructions() {
        VehicleDomainService service = mock(VehicleDomainService.class);
        HttpServletStreamableServerTransportProvider transport = configuration.mcpTransportProvider();

        McpSyncServer server = configuration.mcpServer(
                transport,
                new VehicleInformationTool(service),
                new RegisterVehiclePurchaseTool(service),
                new SetPaymentMethodTool(service),
                objectMapper);

        try {
            assertThat(server.listTools())
                    .extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder(
                            "get_vehicle_information", "register_vehicle_purchase", "set_payment_method");
            assertThat(server.listResources())
                    .extracting(McpSchema.Resource::uri)
                    .contains(McpServerConfiguration.PAYMENT_DEFAULTS_URI);
            assertThat(server.getServerInfo().name()).isEqualTo("mcp-domain");
            assertThat(server.getServerCapabilities()).isNotNull();
        } finally {
            server.close();
        }
    }

    private static McpSchema.CallToolResult invoke(
            SyncToolSpecification specification, Map<String, Object> arguments) {
        return specification.callHandler().apply(null,
                McpSchema.CallToolRequest.builder(specification.tool().name())
                        .arguments(arguments)
                        .build());
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertiesOf(McpSchema.Tool tool) {
        return (Map<String, Object>) tool.inputSchema().get("properties");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertyOf(McpSchema.Tool tool, String name) {
        return (Map<String, Object>) propertiesOf(tool).get(name);
    }
}
