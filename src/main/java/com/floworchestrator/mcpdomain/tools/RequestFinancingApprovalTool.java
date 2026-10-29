package com.floworchestrator.mcpdomain.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Solicita autorización humana para un financiamiento. Sin una decisión
 * explícita la herramienta responde {@code requiresIntervention}, lo que pausa
 * el flujo y crea una intervención en el Control Center. Cuando el operador
 * aprueba, el flujo se reanuda y el agente vuelve a invocarla con
 * {@code decision=APPROVED}. Herramienta MCP {@code request_financing_approval};
 * solo {@code purchase_order_id} es obligatorio.
 */
@Component
@Slf4j
public class RequestFinancingApprovalTool {

    public static final String TOOL_NAME = "request_financing_approval";
    public static final String STATUS_PENDING = "PENDING_HUMAN_APPROVAL";
    public static final String STATUS_APPROVED = "FINANCING_APPROVED";

    private static final String DECISION_APPROVED = "APPROVED";

    /**
     * Ejecuta {@code request_financing_approval}: sin {@code decision} solicita la aprobación
     * humana; con {@code decision=APPROVED} confirma el financiamiento.
     *
     * @throws ToolException si {@code decision} tiene un valor distinto de APPROVED.
     */
    public ToolResponse execute(Map<String, Object> arguments) {
        return ToolSupport.execute(TOOL_NAME, arguments, args -> {
            String purchaseOrderId = ToolArguments.requireString(args, "purchase_order_id", TOOL_NAME);
            String rawDecision = ToolArguments.optionalString(args, "decision", null, TOOL_NAME);
            String decision = rawDecision == null ? null : rawDecision.toUpperCase(Locale.ROOT);
            if (decision != null && !DECISION_APPROVED.equals(decision)) {
                throw ToolException.invalidArgument(TOOL_NAME, "decision", "'APPROVED' o el campo omitido");
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("purchase_order_id", purchaseOrderId);
            if (DECISION_APPROVED.equals(decision)) {
                response.put("status", STATUS_APPROVED);
                response.put("approved", true);
                response.put("message", "Financiamiento aprobado por el operador humano.");
                log.info("[{}] {}: financiamiento aprobado | purchaseOrderId={}",
                        ToolFlowCodes.FINANCING_APPROVAL, TOOL_NAME, purchaseOrderId);
            } else {
                response.put("status", STATUS_PENDING);
                response.put("requiresIntervention", true);
                response.put("reason", "El financiamiento del vehículo requiere autorización de un operador humano.");
                response.put("how_to_resume", "Aprueba la intervención en el Control Center; el flujo se "
                        + "reanuda y esta herramienta debe invocarse de nuevo con decision=APPROVED.");
                log.info("[{}] {}: autorización humana solicitada | purchaseOrderId={}",
                        ToolFlowCodes.FINANCING_APPROVAL, TOOL_NAME, purchaseOrderId);
            }
            return response;
        });
    }
}
