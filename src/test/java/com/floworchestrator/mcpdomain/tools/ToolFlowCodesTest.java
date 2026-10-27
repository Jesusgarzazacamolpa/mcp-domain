package com.floworchestrator.mcpdomain.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolFlowCodesTest {

    @Test
    void mapsEachToolToItsFlowCode() {
        assertThat(ToolFlowCodes.forTool(VehicleInformationTool.TOOL_NAME))
                .isEqualTo(ToolFlowCodes.VEHICLE_INFORMATION);
        assertThat(ToolFlowCodes.forTool(RegisterVehiclePurchaseTool.TOOL_NAME))
                .isEqualTo(ToolFlowCodes.REGISTER_PURCHASE);
        assertThat(ToolFlowCodes.forTool(SetPaymentMethodTool.TOOL_NAME))
                .isEqualTo(ToolFlowCodes.SET_PAYMENT_METHOD);
    }

    @Test
    void returnsUnknownCodeForUnmappedOrMissingTool() {
        assertThat(ToolFlowCodes.forTool("otra_herramienta")).isEqualTo(ToolFlowCodes.UNKNOWN_TOOL);
        assertThat(ToolFlowCodes.forTool(null)).isEqualTo(ToolFlowCodes.UNKNOWN_TOOL);
    }
}
