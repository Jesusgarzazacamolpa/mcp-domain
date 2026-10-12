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

    private final VehicleCatalogRemoteRepository vehicleCatalogRemoteRepository;
    private final PurchaseRemoteRepository purchaseRemoteRepository;
    private final PaymentMethodRemoteRepository paymentMethodRemoteRepository;

    public VehicleDomainService(
            VehicleCatalogRemoteRepository vehicleCatalogRemoteRepository,
            PurchaseRemoteRepository purchaseRemoteRepository,
            PaymentMethodRemoteRepository paymentMethodRemoteRepository) {
        this.vehicleCatalogRemoteRepository = vehicleCatalogRemoteRepository;
        this.purchaseRemoteRepository = purchaseRemoteRepository;
        this.paymentMethodRemoteRepository = paymentMethodRemoteRepository;
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

        if (vehicleCatalogRemoteRepository != null) {
            Map<String, Object> remoteVehicle = vehicleCatalogRemoteRepository.getVehicleById(vehicleId);
            if (remoteVehicle != null && !remoteVehicle.isEmpty()) {
                response.putAll(remoteVehicle);
                response.putIfAbsent("vehicle_id", vehicleId);
            } else {
                throw new IllegalStateException("Vehicle not found: " + vehicleId);
            }
        } else {
            throw new IllegalStateException("Vehicle catalog repository is not available");
        }

        return response;
    }

    /**
     * Registra una compra de vehículo.
     *
     * @param vehicleId      ID del vehículo.
     * @param purchaseOrderId ID de la orden de compra (opcional, se genera si no se proporciona).
     * @return Map con el resultado de la compra.
     * @throws IllegalStateException si el servicio de compras no está disponible.
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
                : "po-" + UUID.randomUUID();

        Map<String, Object> response = new HashMap<>();
        response.put("vehicle_id", vehicleId);
        response.put("purchase_order_id", resolvedPurchaseOrderId);
        response.put("status", "CREATED");
        response.put("message", "Purchase order registered successfully");

        if (customerId != null && !customerId.isBlank()) {
            response.put("customer_id", customerId);
        }
        if (salesAgentId != null && !salesAgentId.isBlank()) {
            response.put("sales_agent_id", salesAgentId);
        }

        if (purchaseRemoteRepository != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("vehicle_id", vehicleId);
            payload.put("purchase_order_id", resolvedPurchaseOrderId);
            if (customerId != null && !customerId.isBlank()) {
                payload.put("customer_id", customerId);
            }
            if (salesAgentId != null && !salesAgentId.isBlank()) {
                payload.put("sales_agent_id", salesAgentId);
            }
            payload.put("status", "CREATED");

            Map<String, Object> remotePurchase = purchaseRemoteRepository.createPurchaseOrder(payload);
            if (remotePurchase != null && !remotePurchase.isEmpty()) {
                response.putAll(remotePurchase);
                response.putIfAbsent("vehicle_id", vehicleId);
                response.putIfAbsent("purchase_order_id", resolvedPurchaseOrderId);
            }
        } else {
            throw new IllegalStateException("Purchase repository is not available");
        }

        return response;
    }

    /**
     * Configura el método de pago para una orden de compra.
     *
     * @param purchaseOrderId ID de la orden de compra.
     * @param paymentType     Tipo de pago (ej. FINANCING).
     * @return Map con el resultado de la configuración.
     * @throws IllegalStateException si el servicio de pagos no está disponible.
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

        String resolvedPaymentType = paymentType != null && !paymentType.isBlank() ? paymentType : "FINANCING";
        String resolvedFinancialInstitution = financialInstitution != null && !financialInstitution.isBlank()
                ? financialInstitution
                : "BBVA";
        Number resolvedDownPaymentAmount = downPaymentAmount != null ? downPaymentAmount : 15000;

        Map<String, Object> response = new HashMap<>();
        response.put("purchase_order_id", purchaseOrderId);
        response.put("status", "PAYMENT_CONFIGURED");
        response.put("payment_type", resolvedPaymentType);
        response.put("financial_institution", resolvedFinancialInstitution);
        response.put("down_payment_amount", resolvedDownPaymentAmount);

        if (paymentMethodRemoteRepository != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("purchase_order_id", purchaseOrderId);
            payload.put("payment_type", resolvedPaymentType);
            payload.put("financial_institution", resolvedFinancialInstitution);
            payload.put("down_payment_amount", resolvedDownPaymentAmount);

            Map<String, Object> remotePayment = paymentMethodRemoteRepository.registerPayment(payload);
            if (remotePayment != null && !remotePayment.isEmpty()) {
                response.putAll(remotePayment);
                response.putIfAbsent("purchase_order_id", purchaseOrderId);
                response.putIfAbsent("status", "PAYMENT_CONFIGURED");
            }
        } else {
            throw new IllegalStateException("Payment method repository is not available");
        }

        return response;
    }
}