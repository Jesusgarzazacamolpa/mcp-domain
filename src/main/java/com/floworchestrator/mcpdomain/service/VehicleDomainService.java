package com.floworchestrator.mcpdomain.service;

import com.floworchestrator.mcpdomain.remotos.PaymentMethodRemoteRepository;
import com.floworchestrator.mcpdomain.remotos.PurchaseRemoteRepository;
import com.floworchestrator.mcpdomain.remotos.VehicleCatalogRemoteRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VehicleDomainService {

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_PAYMENT_CONFIGURED = "PAYMENT_CONFIGURED";
    private static final String DEFAULT_PAYMENT_TYPE = "FINANCING";
    private static final String DEFAULT_FINANCIAL_INSTITUTION = "BBVA";
    private static final int DEFAULT_DOWN_PAYMENT_AMOUNT = 15000;
    private static final String PURCHASE_ORDER_ID_PREFIX = "po-";

    private final VehicleCatalogRemoteRepository vehicleCatalogRemoteRepository;
    private final PurchaseRemoteRepository purchaseRemoteRepository;
    private final PaymentMethodRemoteRepository paymentMethodRepository;

    public VehicleDomainService(
            VehicleCatalogRemoteRepository vehicleCatalogRemoteRepository,
            PurchaseRemoteRepository purchaseRemoteRepository,
            PaymentMethodRemoteRepository paymentMethodRepository) {
        this.vehicleCatalogRemoteRepository = vehicleCatalogRemoteRepository;
        this.purchaseRemoteRepository = purchaseRemoteRepository;
        this.paymentMethodRepository = paymentMethodRepository;
    }

    /**
     * Obtiene la información de un vehículo por su ID.
     * Lanza una excepción si el vehículo no se encuentra.
     *
     * @param vehicleId ID del vehículo a consultar.
     * @return Map con los datos del vehículo.
     * @throws IllegalStateException si el vehículo no existe.
     */
    public Map<String, Object> getVehicleInformation(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("vehicleId is required");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("vehicle_id", vehicleId);

        Map<String, Object> remoteVehicle = vehicleCatalogRemoteRepository.getVehicleById(vehicleId);
        if (remoteVehicle != null && !remoteVehicle.isEmpty()) {
            response.putAll(remoteVehicle);
            response.putIfAbsent("vehicle_id", vehicleId);
        } else {
            throw new IllegalStateException("Vehicle not found: " + vehicleId);
        }

        return response;
    }

    /**
     * Registra una compra de vehículo.
     *
     * @param vehicleId      ID del vehículo.
     * @param purchaseOrderId ID de la orden de compra (opcional, se genera si no se proporciona).
     * @return Map con el resultado de la compra.
     */
    public Map<String, Object> registerVehiclePurchase(String vehicleId, String purchaseOrderId) {
        return registerVehiclePurchase(null, vehicleId, null, purchaseOrderId);
    }

    /**
     * Registra una compra de vehículo con todos los datos.
     *
     * @param customerId    ID del cliente.
     * @param vehicleId     ID del vehículo.
     * @param salesAgentId  ID del agente de ventas.
     * @return Map con el resultado de la compra.
     */
    public Map<String, Object> registerVehiclePurchase(String customerId, String vehicleId, String salesAgentId) {
        return registerVehiclePurchase(customerId, vehicleId, salesAgentId, null);
    }

    private Map<String, Object> registerVehiclePurchase(
            String customerId,
            String vehicleId,
            String salesAgentId,
            String purchaseOrderId) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("vehicleId is required");
        }

        String resolvedPurchaseOrderId = purchaseOrderId != null && !purchaseOrderId.isBlank()
                ? purchaseOrderId
                : PURCHASE_ORDER_ID_PREFIX + UUID.randomUUID();

        Map<String, Object> response = new HashMap<>();
        response.put("vehicle_id", vehicleId);
        response.put("purchase_order_id", resolvedPurchaseOrderId);
        response.put("status", STATUS_CREATED);
        response.put("message", "Purchase order registered successfully");

        if (customerId != null && !customerId.isBlank()) {
            response.put("customer_id", customerId);
        }
        if (salesAgentId != null && !salesAgentId.isBlank()) {
            response.put("sales_agent_id", salesAgentId);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("vehicle_id", vehicleId);
        payload.put("purchase_order_id", resolvedPurchaseOrderId);
        if (customerId != null && !customerId.isBlank()) {
            payload.put("customer_id", customerId);
        }
        if (salesAgentId != null && !salesAgentId.isBlank()) {
            payload.put("sales_agent_id", salesAgentId);
        }
        payload.put("status", STATUS_CREATED);

        Map<String, Object> remotePurchase = purchaseRemoteRepository.createPurchaseOrder(payload);
        if (remotePurchase != null && !remotePurchase.isEmpty()) {
            response.putAll(remotePurchase);
            response.putIfAbsent("vehicle_id", vehicleId);
            response.putIfAbsent("purchase_order_id", resolvedPurchaseOrderId);
        }

        return response;
    }

    /**
     * Configura el método de pago para una orden de compra.
     *
     * @param purchaseOrderId ID de la orden de compra.
     * @param paymentType     Tipo de pago (ej. FINANCING).
     * @return Map con el resultado de la configuración.
     */
    public Map<String, Object> setPaymentMethod(String purchaseOrderId, String paymentType) {
        return setPaymentMethod(purchaseOrderId, paymentType, null, null);
    }

    /**
     * Configura el método de pago para una orden de compra con todos los datos.
     *
     * @param purchaseOrderId     ID de la orden de compra.
     * @param paymentType         Tipo de pago.
     * @param financialInstitution Institución financiera.
     * @param downPaymentAmount   Enganche.
     * @return Map con el resultado de la configuración.
     */
    public Map<String, Object> setPaymentMethod(
            String purchaseOrderId,
            String paymentType,
            String financialInstitution,
            Number downPaymentAmount) {
        if (purchaseOrderId == null || purchaseOrderId.isBlank()) {
            throw new IllegalArgumentException("purchaseOrderId is required");
        }

        String resolvedPaymentType = paymentType != null && !paymentType.isBlank() ? paymentType : DEFAULT_PAYMENT_TYPE;
        String resolvedFinancialInstitution = financialInstitution != null && !financialInstitution.isBlank()
                ? financialInstitution
                : DEFAULT_FINANCIAL_INSTITUTION;
        Number resolvedDownPaymentAmount = downPaymentAmount != null ? downPaymentAmount : DEFAULT_DOWN_PAYMENT_AMOUNT;

        Map<String, Object> response = new HashMap<>();
        response.put("purchase_order_id", purchaseOrderId);
        response.put("status", STATUS_PAYMENT_CONFIGURED);
        response.put("payment_type", resolvedPaymentType);
        response.put("financial_institution", resolvedFinancialInstitution);
        response.put("down_payment_amount", resolvedDownPaymentAmount);

        Map<String, Object> payload = new HashMap<>();
        payload.put("purchase_order_id", purchaseOrderId);
        payload.put("payment_type", resolvedPaymentType);
        payload.put("financial_institution", resolvedFinancialInstitution);
        payload.put("down_payment_amount", resolvedDownPaymentAmount);

        Map<String, Object> remotePayment = paymentMethodRepository.registerPayment(payload);
        if (remotePayment != null && !remotePayment.isEmpty()) {
            response.putAll(remotePayment);
            response.putIfAbsent("purchase_order_id", purchaseOrderId);
            response.putIfAbsent("status", STATUS_PAYMENT_CONFIGURED);
        }

        return response;
    }
}
