package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RegisterVehiclePurchaseTool {

    private static final Logger log = LoggerFactory.getLogger(RegisterVehiclePurchaseTool.class);

    private final VehicleDomainService vehicleDomainService;

    public Map<String, Object> execute(String customerId, String vehicleId, String salesAgentId) {
        log.info("[TOOL] RegisterVehiclePurchaseTool.execute called: customerId={}, vehicleId={}, salesAgentId={}",
                customerId, vehicleId, salesAgentId);
        try {
            Map<String, Object> result = vehicleDomainService.registerVehiclePurchase(customerId, vehicleId, salesAgentId);
            log.info("[TOOL] RegisterVehiclePurchaseTool.execute success: customerId={}, vehicleId={}, salesAgentId={}, result={}",
                    customerId, vehicleId, salesAgentId, result);
            return result;
        } catch (Exception ex) {
            log.error("[TOOL] RegisterVehiclePurchaseTool.execute failed: customerId={}, vehicleId={}, salesAgentId={}, error={}",
                    customerId, vehicleId, salesAgentId, ex.getMessage(), ex);
            throw ex;
        }
    }

    public Map<String, Object> execute(String vehicleId, String purchaseOrderId) {
        log.info("[TOOL] RegisterVehiclePurchaseTool.execute called: vehicleId={}, purchaseOrderId={}",
                vehicleId, purchaseOrderId);
        try {
            Map<String, Object> result = vehicleDomainService.registerVehiclePurchase(vehicleId, purchaseOrderId);
            log.info("[TOOL] RegisterVehiclePurchaseTool.execute success: vehicleId={}, purchaseOrderId={}, result={}",
                    vehicleId, purchaseOrderId, result);
            return result;
        } catch (Exception ex) {
            log.error("[TOOL] RegisterVehiclePurchaseTool.execute failed: vehicleId={}, purchaseOrderId={}, error={}",
                    vehicleId, purchaseOrderId, ex.getMessage(), ex);
            throw ex;
        }
    }
}
