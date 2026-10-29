package com.floworchestrator.mcpdomain.tools;

/**
 * Códigos de flujo que identifican cada herramienta en los logs. Permiten filtrar
 * la traza de una operación sin depender del texto del mensaje.
 */
public final class ToolFlowCodes {

    public static final String VEHICLE_INFORMATION = "MCP_VEHICLE_01";
    public static final String REGISTER_PURCHASE = "MCP_PURCHASE_01";
    public static final String SET_PAYMENT_METHOD = "MCP_PAYMENT_01";
    public static final String FINANCING_APPROVAL = "MCP_FINANCING_01";
    public static final String IDEMPOTENCY = "MCP_IDEMPOTENCY_01";
    public static final String SERVER = "MCP_SERVER_01";
    public static final String UNKNOWN_TOOL = "MCP_TOOL_00";

    private ToolFlowCodes() {
    }

    /**
     * Devuelve el código de flujo asociado a la tool, o {@link #UNKNOWN_TOOL} si no se reconoce.
     */
    public static String forTool(String toolName) {
        if (VehicleInformationTool.TOOL_NAME.equals(toolName)) {
            return VEHICLE_INFORMATION;
        }
        if (RegisterVehiclePurchaseTool.TOOL_NAME.equals(toolName)) {
            return REGISTER_PURCHASE;
        }
        if (SetPaymentMethodTool.TOOL_NAME.equals(toolName)) {
            return SET_PAYMENT_METHOD;
        }
        if (RequestFinancingApprovalTool.TOOL_NAME.equals(toolName)) {
            return FINANCING_APPROVAL;
        }
        return UNKNOWN_TOOL;
    }
}
