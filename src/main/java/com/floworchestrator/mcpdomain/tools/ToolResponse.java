package com.floworchestrator.mcpdomain.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resultado de una herramienta MCP. Los errores se devuelven como datos
 * estructurados para que el modelo pueda corregir su llamada.
 */
public record ToolResponse(Map<String, Object> payload, boolean error) {

    public static ToolResponse success(Map<String, Object> payload) {
        return new ToolResponse(payload == null ? Map.of() : payload, false);
    }

    public static ToolResponse error(
            String tool, String errorCode, String message, String howToFix, String field, boolean retryable) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("tool", tool);
        body.put("error_code", errorCode);
        body.put("message", message);
        body.put("how_to_fix", howToFix);
        if (field != null) {
            body.put("field", field);
        }
        body.put("retryable", retryable);
        return new ToolResponse(body, true);
    }
}
