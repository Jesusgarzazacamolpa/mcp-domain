package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class VehicleInformationTool {

    private static final Logger log = LoggerFactory.getLogger(VehicleInformationTool.class);

    private final VehicleDomainService vehicleDomainService;

    public Map<String, Object> execute(String vehicleId) {
        log.info("[TOOL] VehicleInformationTool.execute called: vehicleId={}", vehicleId);
        try {
            Map<String, Object> result = vehicleDomainService.getVehicleInformation(vehicleId);
            log.info("[TOOL] VehicleInformationTool.execute success: vehicleId={}, result={}",
                    vehicleId, result);
            return result;
        } catch (Exception ex) {
            log.error("[TOOL] VehicleInformationTool.execute failed: vehicleId={}, error={}",
                    vehicleId, ex.getMessage(), ex);
            throw ex;
        }
    }
}
