package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool MCP {@code get_vehicle_information}: expone la consulta de solo lectura de un vehículo
 * del catálogo y devuelve sus datos y disponibilidad.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VehicleInformationTool {

    public static final String TOOL_NAME = "get_vehicle_information";

    private final VehicleDomainService vehicleDomainService;

    /**
     * Ejecuta {@code get_vehicle_information}; requiere {@code vehicle_id} y devuelve los datos
     * del vehículo o una respuesta de error estructurada.
     */
    public ToolResponse execute(Map<String, Object> arguments) {
        return ToolSupport.execute(TOOL_NAME, arguments, args -> {
            String vehicleId = ToolArguments.requireString(args, "vehicle_id", TOOL_NAME);
            log.info("[{}] {}: consultando catálogo | vehicleId={}",
                    ToolFlowCodes.VEHICLE_INFORMATION, TOOL_NAME, vehicleId);
            return vehicleDomainService.getVehicleInformation(vehicleId);
        });
    }
}
