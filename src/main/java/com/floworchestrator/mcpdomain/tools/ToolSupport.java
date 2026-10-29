package com.floworchestrator.mcpdomain.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;

/**
 * Ejecuta una herramienta garantizando que ninguna excepción escape al servidor MCP.
 */
public final class ToolSupport {

    private static final Logger log = LoggerFactory.getLogger(ToolSupport.class);

    private ToolSupport() {
    }

    /**
     * Ejecuta la acción con argumentos no nulos, mide la duración y convierte cualquier excepción
     * en una respuesta de error, de modo que ningún fallo escape al servidor MCP.
     */
    public static ToolResponse execute(
            String toolName, Map<String, Object> arguments, Function<Map<String, Object>, Map<String, Object>> action) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        String flowCode = ToolFlowCodes.forTool(toolName);
        long startNanos = System.nanoTime();
        log.info("[{}] {}: ejecución iniciada | argumentos={}", flowCode, toolName, safeArguments.keySet());
        try {
            ToolResponse response = ToolResponse.success(action.apply(safeArguments));
            log.info("[{}] {}: ejecución completada | status=OK, durationMs={}",
                    flowCode, toolName, elapsedMillis(startNanos));
            return response;
        } catch (Exception exception) {
            ToolResponse response = ToolErrors.toResponse(toolName, exception);
            log.warn("[{}] {}: ejecución finalizada con error | status=ERROR, errorCode={}, durationMs={}",
                    flowCode, toolName, response.payload().get("error_code"), elapsedMillis(startNanos));
            return response;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
