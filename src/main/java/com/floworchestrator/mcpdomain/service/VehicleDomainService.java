package com.floworchestrator.mcpdomain.service;

import com.floworchestrator.mcpdomain.remotos.PaymentMethodRemoteRepository;
import com.floworchestrator.mcpdomain.remotos.PurchaseRemoteRepository;
import com.floworchestrator.mcpdomain.remotos.VehicleCatalogRemoteRepository;
import com.floworchestrator.mcpdomain.tools.ToolFlowCodes;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class VehicleDomainService {

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_PAYMENT_CONFIGURED = "PAYMENT_CONFIGURED";
    private static final String PURCHASE_ORDER_ID_PREFIX = "po-";
    private static final String KEY_SEPARATOR = "\u0001";

    private final VehicleCatalogRemoteRepository vehicleCatalogRemoteRepository;
    private final PurchaseRemoteRepository purchaseRemoteRepository;
    private final PaymentMethodRemoteRepository paymentMethodRepository;
    private final IdempotencyStore idempotencyStore;

    public VehicleDomainService(
            VehicleCatalogRemoteRepository vehicleCatalogRemoteRepository,
            PurchaseRemoteRepository purchaseRemoteRepository,
            PaymentMethodRemoteRepository paymentMethodRepository,
            IdempotencyStore idempotencyStore) {
        this.vehicleCatalogRemoteRepository = vehicleCatalogRemoteRepository;
        this.purchaseRemoteRepository = purchaseRemoteRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.idempotencyStore = idempotencyStore;
    }

    /**
     * Obtiene la información de catálogo de un vehículo.
     *
     * @throws VehicleNotFoundException si el vehículo no existe en el catálogo.
     */
    public Map<String, Object> getVehicleInformation(String vehicleId) {
        requireText(vehicleId, "vehicleId");

        log.info("[{}] Consultando catálogo de vehículos | vehicleId={}",
                ToolFlowCodes.VEHICLE_INFORMATION, vehicleId);
        Map<String, Object> remoteVehicle = vehicleCatalogRemoteRepository.getVehicleById(vehicleId);
        if (remoteVehicle == null || remoteVehicle.isEmpty()) {
            log.warn("[{}] Vehículo no encontrado en el catálogo | vehicleId={}",
                    ToolFlowCodes.VEHICLE_INFORMATION, vehicleId);
            throw new VehicleNotFoundException(vehicleId);
        }
        log.info("[{}] Vehículo encontrado | vehicleId={}, modelId={}, availability={}",
                ToolFlowCodes.VEHICLE_INFORMATION, vehicleId,
                remoteVehicle.get("model_id"), remoteVehicle.get("availability"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vehicle_id", vehicleId);
        response.putAll(remoteVehicle);
        response.putIfAbsent("vehicle_id", vehicleId);
        return response;
    }

    /**
     * Registra una compra. La orden de compra es determinista para la misma clave de
     * idempotencia, de modo que un reintento no genera una orden duplicada.
     */
    public Map<String, Object> registerVehiclePurchase(
            String customerId, String vehicleId, String salesAgentId, String idempotencyKey) {
        requireText(customerId, "customerId");
        requireText(vehicleId, "vehicleId");
        requireText(salesAgentId, "salesAgentId");

        // La clave incluye los datos de la compra: una clave explícita reutilizada
        // con otro vehículo/cliente no debe apuntar a la misma orden, porque el
        // dominio la rechazaría con un conflicto permanente.
        String businessKey = stableId(customerId, vehicleId, salesAgentId);
        String key = idempotencyKey != null && !idempotencyKey.isBlank()
                ? "purchase:key:" + stableId(businessKey, idempotencyKey.trim())
                : "purchase:derived:" + businessKey;
        String purchaseOrderId = PURCHASE_ORDER_ID_PREFIX + stableId(key);
        log.info("[{}] Preparando orden de compra | purchaseOrderId={}, origenClave={}",
                ToolFlowCodes.REGISTER_PURCHASE, purchaseOrderId,
                idempotencyKey != null && !idempotencyKey.isBlank() ? "idempotency_key" : "derivada");

        Map<String, Object> result = idempotencyStore.getOrCompute(key,
                () -> createPurchaseOrder(customerId, vehicleId, salesAgentId, purchaseOrderId));
        log.info("[{}] Compra procesada | purchaseOrderId={}, status={}, idempotentReplay={}",
                ToolFlowCodes.REGISTER_PURCHASE, result.get("purchase_order_id"),
                result.get("status"), result.get("idempotent_replay"));
        return result;
    }

    /**
     * Configura el método de pago. Los valores omitidos usan los valores por defecto.
     */
    public Map<String, Object> setPaymentMethod(
            String purchaseOrderId,
            String paymentType,
            String financialInstitution,
            Number downPaymentAmount) {
        requireText(purchaseOrderId, "purchaseOrderId");
        String resolvedPaymentType = defaultIfBlank(paymentType, PaymentDefaults.PAYMENT_TYPE);
        String resolvedFinancialInstitution = defaultIfBlank(
                financialInstitution, PaymentDefaults.FINANCIAL_INSTITUTION);
        Number resolvedDownPaymentAmount = downPaymentAmount != null
                ? downPaymentAmount
                : PaymentDefaults.DOWN_PAYMENT_AMOUNT;
        if (resolvedDownPaymentAmount.doubleValue() < 0) {
            throw new IllegalArgumentException("downPaymentAmount debe ser cero o positivo");
        }

        String key = "payment:" + stableId(
                purchaseOrderId, resolvedPaymentType, resolvedFinancialInstitution, amountKey(resolvedDownPaymentAmount));
        log.info("[{}] Preparando configuración de pago | purchaseOrderId={}, paymentType={}, "
                        + "financialInstitution={}, downPaymentAmount={}",
                ToolFlowCodes.SET_PAYMENT_METHOD, purchaseOrderId, resolvedPaymentType,
                resolvedFinancialInstitution, resolvedDownPaymentAmount);
        Map<String, Object> result = idempotencyStore.getOrCompute(key, () -> registerPayment(
                purchaseOrderId, resolvedPaymentType, resolvedFinancialInstitution, resolvedDownPaymentAmount));
        log.info("[{}] Pago procesado | purchaseOrderId={}, status={}, idempotentReplay={}",
                ToolFlowCodes.SET_PAYMENT_METHOD, result.get("purchase_order_id"),
                result.get("status"), result.get("idempotent_replay"));
        return result;
    }

    private Map<String, Object> createPurchaseOrder(
            String customerId, String vehicleId, String salesAgentId, String purchaseOrderId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vehicle_id", vehicleId);
        response.put("purchase_order_id", purchaseOrderId);
        response.put("customer_id", customerId);
        response.put("sales_agent_id", salesAgentId);
        response.put("status", STATUS_CREATED);
        response.put("message", "Orden de compra registrada correctamente");

        Map<String, Object> payload = new HashMap<>();
        payload.put("vehicle_id", vehicleId);
        payload.put("purchase_order_id", purchaseOrderId);
        payload.put("customer_id", customerId);
        payload.put("sales_agent_id", salesAgentId);
        payload.put("status", STATUS_CREATED);

        Map<String, Object> remotePurchase;
        try {
            remotePurchase = purchaseRemoteRepository.createPurchaseOrder(payload);
        } catch (FeignException.Conflict conflict) {
            // El identificador se deriva de los datos, así que un conflicto significa
            // que la orden ya existía (por ejemplo, con un vehicle-domain sin reiniciar).
            log.info("[{}] La orden ya existía en el dominio; se reutiliza | purchaseOrderId={}, status={}",
                    ToolFlowCodes.REGISTER_PURCHASE, purchaseOrderId, conflict.status());
            response.put("message", "La orden ya estaba registrada; se reutiliza la existente");
            response.put("reused_existing_order", true);
            return response;
        }
        if (remotePurchase != null && !remotePurchase.isEmpty()) {
            response.putAll(remotePurchase);
            response.putIfAbsent("vehicle_id", vehicleId);
            response.putIfAbsent("purchase_order_id", purchaseOrderId);
        }
        log.info("[{}] Orden de compra registrada | purchaseOrderId={}, status={}",
                ToolFlowCodes.REGISTER_PURCHASE, response.get("purchase_order_id"), response.get("status"));
        return response;
    }

    private Map<String, Object> registerPayment(
            String purchaseOrderId, String paymentType, String financialInstitution, Number downPaymentAmount) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("purchase_order_id", purchaseOrderId);
        response.put("status", STATUS_PAYMENT_CONFIGURED);
        response.put("payment_type", paymentType);
        response.put("financial_institution", financialInstitution);
        response.put("down_payment_amount", downPaymentAmount);

        Map<String, Object> payload = new HashMap<>();
        payload.put("purchase_order_id", purchaseOrderId);
        payload.put("payment_type", paymentType);
        payload.put("financial_institution", financialInstitution);
        payload.put("down_payment_amount", downPaymentAmount);

        Map<String, Object> remotePayment = paymentMethodRepository.registerPayment(payload);
        if (remotePayment != null && !remotePayment.isEmpty()) {
            response.putAll(remotePayment);
            response.putIfAbsent("purchase_order_id", purchaseOrderId);
            response.putIfAbsent("status", STATUS_PAYMENT_CONFIGURED);
        }
        log.info("[{}] Método de pago registrado | purchaseOrderId={}, status={}",
                ToolFlowCodes.SET_PAYMENT_METHOD, response.get("purchase_order_id"), response.get("status"));
        return response;
    }

    private static String stableId(String... parts) {
        String joined = String.join(KEY_SEPARATOR, parts);
        return UUID.nameUUIDFromBytes(joined.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String amountKey(Number amount) {
        return new BigDecimal(amount.toString()).stripTrailingZeros().toPlainString();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value.trim() : defaultValue;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El campo '" + field + "' es obligatorio");
        }
    }
}
