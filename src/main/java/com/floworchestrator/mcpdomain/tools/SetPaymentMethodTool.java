package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.PaymentDefaults;
import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool MCP {@code set_payment_method}: configura el pago de una orden de compra y sustituye
 * los argumentos omitidos por los valores por defecto de {@link PaymentDefaults}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SetPaymentMethodTool {

    public static final String TOOL_NAME = "set_payment_method";

    private final VehicleDomainService vehicleDomainService;

    /**
     * Ejecuta {@code set_payment_method}; solo {@code purchase_order_id} es obligatorio y devuelve
     * el método de pago resuelto con su estado, o un error estructurado.
     */
    public ToolResponse execute(Map<String, Object> arguments) {
        return ToolSupport.execute(TOOL_NAME, arguments, args -> {
            String purchaseOrderId = ToolArguments.requireString(args, "purchase_order_id", TOOL_NAME);
            String paymentType = ToolArguments.optionalString(
                    args, "payment_type", PaymentDefaults.PAYMENT_TYPE, TOOL_NAME);
            String financialInstitution = ToolArguments.optionalString(
                    args, "financial_institution", PaymentDefaults.FINANCIAL_INSTITUTION, TOOL_NAME);
            Number downPaymentAmount = ToolArguments.optionalNumber(
                    args, "down_payment_amount", PaymentDefaults.DOWN_PAYMENT_AMOUNT, TOOL_NAME);
            log.info("[{}] {}: configurando pago | purchaseOrderId={}, paymentType={}, "
                            + "financialInstitution={}, downPaymentAmount={}",
                    ToolFlowCodes.SET_PAYMENT_METHOD, TOOL_NAME, purchaseOrderId, paymentType,
                    financialInstitution, downPaymentAmount);
            return vehicleDomainService.setPaymentMethod(
                    purchaseOrderId, paymentType, financialInstitution, downPaymentAmount);
        });
    }
}
