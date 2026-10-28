package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.VehicleNotFoundException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traduce excepciones a respuestas MCP con instrucciones para el modelo.
 */
public final class ToolErrors {

    private static final Logger log = LoggerFactory.getLogger(ToolErrors.class);

    private ToolErrors() {
    }

    public static ToolResponse toResponse(String toolName, Exception exception) {
        String flowCode = ToolFlowCodes.forTool(toolName);
        if (exception instanceof ToolException toolException) {
            log.warn("[{}] {} rechazó la entrada: code={}, field={}, error={}",
                    flowCode, toolName, toolException.errorCode(), toolException.field(), toolException.getMessage());
            return ToolResponse.error(toolName, toolException.errorCode(), toolException.getMessage(),
                    toolException.howToFix(), toolException.field(), toolException.retryable());
        }
        if (exception instanceof VehicleNotFoundException notFound) {
            log.warn("[{}] {} no encontró el vehículo {}", flowCode, toolName, notFound.vehicleId());
            return ToolResponse.error(toolName, "VEHICLE_NOT_FOUND", exception.getMessage(),
                    "Pide al usuario que confirme el identificador del vehículo: no existe en el catálogo. "
                            + "No reintentes con el mismo id.",
                    "vehicle_id", false);
        }
        if (exception instanceof FeignException.NotFound) {
            log.warn("[{}] {} recurso downstream no encontrado: {}", flowCode, toolName, exception.getMessage());
            return ToolResponse.error(toolName, "RESOURCE_NOT_FOUND",
                    "El servicio downstream indicó que el recurso solicitado no existe.",
                    "Confirma los identificadores con el usuario antes de reintentar. No repitas la misma llamada.",
                    null, false);
        }
        if (exception instanceof FeignException.Conflict conflict) {
            log.info("[{}] {} operación ya registrada: status={}", flowCode, toolName, conflict.status());
            return ToolResponse.error(toolName, "ALREADY_EXISTS",
                    "La operación ya fue registrada previamente con esos mismos datos.",
                    "No repitas la llamada: usa el recurso ya existente y continúa con el siguiente paso.",
                    null, false);
        }
        if (exception instanceof FeignException feignException) {
            int status = feignException.status();
            if (status >= 400 && status < 500) {
                log.warn("[{}] {} rechazó la solicitud: status={}, error={}",
                        flowCode, toolName, status, exception.getMessage());
                return ToolResponse.error(toolName, "INVALID_ARGUMENT",
                        "El servicio downstream rechazó la solicitud (estado HTTP " + status + ").",
                        "Corrige los argumentos enviados antes de volver a llamar a la herramienta.",
                        null, false);
            }
            log.error("[{}] {} fallo downstream: status={}, error={}",
                    flowCode, toolName, status, exception.getMessage());
            return ToolResponse.error(toolName, "DEPENDENCY_UNAVAILABLE",
                    "Un servicio downstream falló al procesar la solicitud (estado HTTP "
                            + status + ").",
                    "El fallo es transitorio: reintenta la misma llamada una vez. "
                            + "Si vuelve a fallar, informa al usuario que la operación no se pudo completar.",
                    null, true);
        }
        if (exception instanceof IllegalArgumentException) {
            log.warn("[{}] {} solicitud inválida: {}", flowCode, toolName, exception.getMessage());
            return ToolResponse.error(toolName, "INVALID_ARGUMENT", exception.getMessage(),
                    "Revisa los argumentos antes de volver a llamar a la herramienta.", null, false);
        }
        log.error("[{}] {} falló de forma inesperada", flowCode, toolName, exception);
        return ToolResponse.error(toolName, "INTERNAL_ERROR",
                "La herramienta falló de forma inesperada y no pudo completar la solicitud.",
                "No reintentes la misma llamada. Informa el fallo al usuario o a soporte para que se investigue.",
                null, false);
    }
}
