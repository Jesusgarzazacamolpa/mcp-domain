package com.floworchestrator.mcpdomain.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floworchestrator.mcpdomain.service.PaymentDefaults;
import com.floworchestrator.mcpdomain.tools.RegisterVehiclePurchaseTool;
import com.floworchestrator.mcpdomain.tools.SetPaymentMethodTool;
import com.floworchestrator.mcpdomain.tools.ToolErrors;
import com.floworchestrator.mcpdomain.tools.ToolFlowCodes;
import com.floworchestrator.mcpdomain.tools.ToolResponse;
import com.floworchestrator.mcpdomain.tools.VehicleInformationTool;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.Servlet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Configuration
@Slf4j
public class McpServerConfiguration {

    public static final String PAYMENT_DEFAULTS_URI = "floworchestrator://domain/payment-defaults";
    private static final String JSON_MIME_TYPE = "application/json";
    private static final String SERVER_VERSION = "1.0.0";

    private static final String SERVER_INSTRUCTIONS = """
            Este servidor administra el flujo de compra de vehículos.
            Flujo: (1) llama a get_vehicle_information para confirmar que el vehículo existe y está disponible;
            (2) llama a register_vehicle_purchase con los identificadores del cliente, el vehículo y el agente
            de ventas; (3) llama a set_payment_method con el purchase_order_id devuelto en el paso 2.
            Las herramientas que modifican datos son idempotentes: reintentar una llamada fallida con los mismos
            argumentos devuelve el resultado original en lugar de crear un duplicado. Nunca inventes
            identificadores: si falta un valor obligatorio, pídelo al usuario. Cuando una herramienta devuelva
            status=ERROR, lee how_to_fix y síguelo antes de reintentar.
            """;

    private static final String SERIALIZATION_ERROR_PAYLOAD = """
            {"status":"ERROR","error_code":"SERIALIZATION_ERROR","message":"La herramienta generó una \
            respuesta que no se pudo serializar.","how_to_fix":"No reintentes la misma llamada; informa el \
            fallo al usuario.","retryable":false}""";

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
                .serverInfo("mcp-domain", SERVER_VERSION)
                .instructions(SERVER_INSTRUCTIONS)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .build())
                // Las herramientas devuelven mensajes correctivos, así que se omite el validador genérico.
                .validateToolInputs(false)
                .build();
        server.addTool(vehicleInformationTool(vehicleInformationTool, objectMapper));
        server.addTool(registerVehiclePurchaseTool(registerVehiclePurchaseTool, objectMapper));
        server.addTool(setPaymentMethodTool(setPaymentMethodTool, objectMapper));
        server.addResource(paymentDefaultsResource(objectMapper));
        log.info("[{}] Servidor MCP inicializado | tools=[{}, {}, {}], resource={}",
                ToolFlowCodes.SERVER,
                VehicleInformationTool.TOOL_NAME,
                RegisterVehiclePurchaseTool.TOOL_NAME,
                SetPaymentMethodTool.TOOL_NAME,
                PAYMENT_DEFAULTS_URI);
        return server;
    }

    SyncToolSpecification vehicleInformationTool(
            VehicleInformationTool vehicleInformationTool, ObjectMapper objectMapper) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("vehicle_id", stringProperty(
                "Identificador exacto del vehículo en el catálogo, por ejemplo 'v-101'. Nunca uses una marca "
                        + "ni un nombre de modelo. Si el usuario no proporcionó un identificador, pídelo en "
                        + "lugar de adivinarlo.",
                "v-101"));

        McpSchema.Tool tool = McpSchema.Tool.builder(
                        VehicleInformationTool.TOOL_NAME, objectSchema(properties, List.of("vehicle_id")))
                .title("Obtener información del vehículo")
                .description("""
                        Lee el registro de catálogo de un vehículo. Úsala antes de cotizar un precio o crear \
                        una compra, para confirmar que el vehículo existe y está disponible. Devuelve \
                        vehicle_id, model_id, brand, model, price, currency y availability. Esta herramienta \
                        es de solo lectura y es seguro reintentar la llamada. Si la respuesta tiene \
                        error_code=VEHICLE_NOT_FOUND, pide al usuario que confirme el identificador en lugar \
                        de reintentar con el mismo valor.""")
                .annotations(McpSchema.ToolAnnotations.builder()
                        .title("Obtener información del vehículo")
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .openWorldHint(false)
                        .build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handle(VehicleInformationTool.TOOL_NAME, objectMapper,
                        () -> vehicleInformationTool.execute(request.arguments())))
                .build();
    }

    SyncToolSpecification registerVehiclePurchaseTool(
            RegisterVehiclePurchaseTool registerVehiclePurchaseTool, ObjectMapper objectMapper) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("customer_id", stringProperty(
                "Identificador del cliente que compra el vehículo. Obligatorio; pídelo al usuario si no lo "
                        + "conoces.",
                "c-1001"));
        properties.put("vehicle_id", stringProperty(
                "Identificador del vehículo en el catálogo, por ejemplo 'v-101'. Llama primero a "
                        + "get_vehicle_information para confirmar que el vehículo existe y está disponible.",
                "v-101"));
        properties.put("sales_agent_id", stringProperty(
                "Identificador del agente de ventas que atiende esta compra.", "a-42"));
        properties.put("idempotency_key", stringProperty(
                "Clave estable opcional para este intento de compra (máximo 128 caracteres). Reutiliza "
                        + "exactamente el mismo valor cuando reintentes la misma operación para que no se cree "
                        + "una compra duplicada. Si se omite, la clave se deriva de customer_id, vehicle_id y "
                        + "sales_agent_id, de modo que reintentar con argumentos idénticos devuelve la orden "
                        + "original. Genera una clave nueva solo cuando el usuario quiera explícitamente una "
                        + "compra idéntica adicional.",
                "compra-2026-0001"));

        McpSchema.Tool tool = McpSchema.Tool.builder(
                        RegisterVehiclePurchaseTool.TOOL_NAME,
                        objectSchema(properties, List.of("customer_id", "vehicle_id", "sales_agent_id")))
                .title("Registrar compra de vehículo")
                .description("""
                        Crea una orden de compra que vincula a un cliente, un vehículo y un agente de ventas. \
                        Llama primero a get_vehicle_information para confirmar que el vehículo existe. Esta \
                        herramienta escribe datos y es idempotente: reintentar con los mismos argumentos, o \
                        con la misma idempotency_key, devuelve la orden de compra original con \
                        idempotent_replay=true en lugar de crear un duplicado. Devuelve purchase_order_id, \
                        status y los identificadores utilizados. Siguiente paso: llama a set_payment_method \
                        con el purchase_order_id devuelto. Si la respuesta tiene status=ERROR, sigue how_to_fix \
                        antes de reintentar.""")
                .annotations(McpSchema.ToolAnnotations.builder()
                        .title("Registrar compra de vehículo")
                        .readOnlyHint(false)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .openWorldHint(false)
                        .build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handle(RegisterVehiclePurchaseTool.TOOL_NAME, objectMapper,
                        () -> registerVehiclePurchaseTool.execute(request.arguments())))
                .build();
    }

    SyncToolSpecification setPaymentMethodTool(
            SetPaymentMethodTool setPaymentMethodTool, ObjectMapper objectMapper) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("purchase_order_id", stringProperty(
                "Identificador de la orden de compra devuelto por register_vehicle_purchase. Obligatorio.",
                "po-9f2c1e"));
        properties.put("payment_type", optionalStringProperty(
                "Tipo de pago, por ejemplo 'FINANCING' o 'CASH'. Opcional; por defecto "
                        + PaymentDefaults.PAYMENT_TYPE + ".",
                PaymentDefaults.PAYMENT_TYPE));
        properties.put("financial_institution", optionalStringProperty(
                "Institución financiera usada para el financiamiento. Opcional; por defecto "
                        + PaymentDefaults.FINANCIAL_INSTITUTION + ". Pregunta al usuario cuando el tipo de "
                        + "pago seleccionado requiera una institución específica.",
                PaymentDefaults.FINANCIAL_INSTITUTION));
        properties.put("down_payment_amount", numberProperty(
                "Monto del enganche en MXN. Opcional; por defecto " + PaymentDefaults.DOWN_PAYMENT_AMOUNT
                        + " MXN. Debe ser cero o un número positivo. Pregunta al usuario en lugar de asumir "
                        + "un valor diferente.",
                PaymentDefaults.DOWN_PAYMENT_AMOUNT));

        McpSchema.Tool tool = McpSchema.Tool.builder(
                        SetPaymentMethodTool.TOOL_NAME, objectSchema(properties, List.of("purchase_order_id")))
                .title("Configurar método de pago")
                .description("""
                        Configura cómo se pagará una orden de compra. Llama primero a \
                        register_vehicle_purchase para obtener el purchase_order_id. Solo purchase_order_id \
                        es obligatorio; los campos opcionales omitidos usan los valores por defecto publicados \
                        en el recurso payment-defaults. La llamada es idempotente para los mismos valores \
                        resueltos: un reintento devuelve el resultado almacenado con idempotent_replay=true \
                        en lugar de registrar el pago dos veces. Cambiar cualquier campo configura los nuevos \
                        valores. Si la respuesta tiene status=ERROR, sigue how_to_fix antes de reintentar.""")
                .annotations(McpSchema.ToolAnnotations.builder()
                        .title("Configurar método de pago")
                        .readOnlyHint(false)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .openWorldHint(false)
                        .build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handle(SetPaymentMethodTool.TOOL_NAME, objectMapper,
                        () -> setPaymentMethodTool.execute(request.arguments())))
                .build();
    }

    SyncResourceSpecification paymentDefaultsResource(ObjectMapper objectMapper) {
        McpSchema.Resource resource = McpSchema.Resource.builder()
                .uri(PAYMENT_DEFAULTS_URI)
                .name("payment-defaults")
                .title("Valores por defecto del método de pago")
                .description("Configuración de pago por defecto que set_payment_method aplica cuando se "
                        + "omiten los argumentos opcionales.")
                .mimeType(JSON_MIME_TYPE)
                .build();

        return new SyncResourceSpecification(resource, (exchange, request) ->
                new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(
                        PAYMENT_DEFAULTS_URI, JSON_MIME_TYPE, paymentDefaultsJson(objectMapper)))));
    }

    private McpSchema.CallToolResult handle(
            String toolName, ObjectMapper objectMapper, Supplier<ToolResponse> call) {
        try {
            return toCallResult(objectMapper, call.get());
        } catch (Exception exception) {
            return toCallResult(objectMapper, ToolErrors.toResponse(toolName, exception));
        }
    }

    private McpSchema.CallToolResult toCallResult(ObjectMapper objectMapper, ToolResponse response) {
        try {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(objectMapper.writeValueAsString(response.payload()))
                    .isError(response.error())
                    .build();
        } catch (JsonProcessingException exception) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(SERIALIZATION_ERROR_PAYLOAD)
                    .isError(true)
                    .build();
        }
    }

    private String paymentDefaultsJson(ObjectMapper objectMapper) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payment_type", PaymentDefaults.PAYMENT_TYPE);
        payload.put("financial_institution", PaymentDefaults.FINANCIAL_INSTITUTION);
        payload.put("down_payment_amount", PaymentDefaults.DOWN_PAYMENT_AMOUNT);
        payload.put("currency", "MXN");
        payload.put("notes",
                "set_payment_method aplica estos valores cuando se omite el argumento opcional correspondiente.");
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return SERIALIZATION_ERROR_PAYLOAD;
        }
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> stringProperty(String description, String example) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        property.put("minLength", 1);
        property.put("maxLength", 128);
        property.put("examples", List.of(example));
        return property;
    }

    private Map<String, Object> optionalStringProperty(String description, String defaultValue) {
        Map<String, Object> property = stringProperty(description, defaultValue);
        property.put("default", defaultValue);
        return property;
    }

    private Map<String, Object> numberProperty(String description, Number defaultValue) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "number");
        property.put("description", description);
        property.put("minimum", 0);
        property.put("default", defaultValue);
        property.put("examples", List.of(defaultValue));
        return property;
    }
}
