package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegisterVehiclePurchaseTool {

    public static final String TOOL_NAME = "register_vehicle_purchase";

    private final VehicleDomainService vehicleDomainService;

    public ToolResponse execute(Map<String, Object> arguments) {
        return ToolSupport.execute(TOOL_NAME, arguments, args -> {
            String customerId = ToolArguments.requireString(args, "customer_id", TOOL_NAME);
            String vehicleId = ToolArguments.requireString(args, "vehicle_id", TOOL_NAME);
            String salesAgentId = ToolArguments.requireString(args, "sales_agent_id", TOOL_NAME);
            String idempotencyKey = ToolArguments.optionalIdempotencyKey(args, TOOL_NAME);
            log.info("[{}] {}: registrando compra | customerId={}, vehicleId={}, salesAgentId={}, "
                            + "claveIdempotencia={}",
                    ToolFlowCodes.REGISTER_PURCHASE, TOOL_NAME, customerId, vehicleId, salesAgentId,
                    idempotencyKey == null ? "derivada" : "proporcionada");
            return vehicleDomainService.registerVehiclePurchase(
                    customerId, vehicleId, salesAgentId, idempotencyKey);
        });
    }
}
