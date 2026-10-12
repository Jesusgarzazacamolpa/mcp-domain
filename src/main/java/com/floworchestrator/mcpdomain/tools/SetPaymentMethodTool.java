package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SetPaymentMethodTool {

    private static final Logger log = LoggerFactory.getLogger(SetPaymentMethodTool.class);

    private final VehicleDomainService vehicleDomainService;

    public Map<String, Object> execute(String purchaseOrderId, String paymentType) {
        return execute(purchaseOrderId, paymentType, null, null);
    }

    public Map<String, Object> execute(
            String purchaseOrderId,
            String paymentType,
            String financialInstitution,
            Number downPaymentAmount) {
        log.info("[TOOL] SetPaymentMethodTool.execute called: purchaseOrderId={}, paymentType={}, financialInstitution={}, downPaymentAmount={}",
                purchaseOrderId, paymentType, financialInstitution, downPaymentAmount);
        try {
            Map<String, Object> result = vehicleDomainService.setPaymentMethod(
                    purchaseOrderId, paymentType, financialInstitution, downPaymentAmount);
            log.info("[TOOL] SetPaymentMethodTool.execute success: purchaseOrderId={}, paymentType={}, result={}",
                    purchaseOrderId, paymentType, result);
            return result;
        } catch (Exception ex) {
            log.error("[TOOL] SetPaymentMethodTool.execute failed: purchaseOrderId={}, paymentType={}, error={}",
                    purchaseOrderId, paymentType, ex.getMessage(), ex);
            throw ex;
        }
    }
}
