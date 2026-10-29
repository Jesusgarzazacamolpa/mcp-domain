package com.floworchestrator.mcpdomain.tools;

import java.util.Map;

/**
 * Extrae y valida los argumentos de una herramienta. Cualquier problema lanza
 * {@link ToolException} con instrucciones de autocorrección para el modelo.
 */
public final class ToolArguments {

    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private ToolArguments() {
    }

    /**
     * Obtiene un texto obligatorio ya recortado; rechaza valores ausentes, no textuales, vacíos
     * o de más de 128 caracteres con un {@link ToolException}.
     */
    public static String requireString(Map<String, Object> arguments, String field, String toolName) {
        Object value = arguments.get(field);
        if (value == null) {
            throw ToolException.missingArgument(toolName, field, hintFor(field));
        }
        if (!(value instanceof String text)) {
            throw ToolException.invalidArgument(toolName, field, "una cadena de texto no vacía");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw ToolException.missingArgument(toolName, field, hintFor(field));
        }
        if (trimmed.length() > MAX_IDENTIFIER_LENGTH) {
            throw ToolException.invalidArgument(
                    toolName, field, "una cadena de texto de máximo " + MAX_IDENTIFIER_LENGTH + " caracteres");
        }
        return trimmed;
    }

    /**
     * Devuelve el texto recortado del campo o {@code defaultValue} si falta o está vacío;
     * valida que el valor recibido sea una cadena.
     */
    public static String optionalString(
            Map<String, Object> arguments, String field, String defaultValue, String toolName) {
        Object value = arguments.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof String text)) {
            throw ToolException.invalidArgument(toolName, field, "una cadena de texto");
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    /**
     * Devuelve el número del campo o {@code defaultValue} si falta; rechaza valores no numéricos,
     * no finitos o negativos.
     */
    public static Number optionalNumber(
            Map<String, Object> arguments, String field, Number defaultValue, String toolName) {
        Object value = arguments.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw ToolException.invalidArgument(toolName, field, "un número JSON finito");
        }
        if (number.doubleValue() < 0) {
            throw ToolException.invalidArgument(toolName, field, "cero o un número JSON positivo");
        }
        return number;
    }

    /**
     * Lee {@code idempotency_key} de forma opcional y valida que no exceda 128 caracteres.
     */
    public static String optionalIdempotencyKey(Map<String, Object> arguments, String toolName) {
        String key = optionalString(arguments, "idempotency_key", null, toolName);
        if (key != null && key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ToolException.invalidArgument(
                    toolName, "idempotency_key",
                    "una cadena de texto de máximo " + MAX_IDEMPOTENCY_KEY_LENGTH + " caracteres");
        }
        return key;
    }

    private static String hintFor(String field) {
        return switch (field) {
            case "vehicle_id" -> "el identificador del vehículo en el catálogo (por ejemplo 'v-101')";
            case "customer_id" -> "el identificador del cliente";
            case "sales_agent_id" -> "el identificador del agente de ventas";
            case "purchase_order_id" ->
                    "el identificador de la orden de compra devuelto por register_vehicle_purchase";
            default -> "un valor para '" + field + "'";
        };
    }
}
