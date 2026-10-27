package com.floworchestrator.mcpdomain.service;

import com.floworchestrator.mcpdomain.remotos.PaymentMethodRemoteRepository;
import com.floworchestrator.mcpdomain.remotos.PurchaseRemoteRepository;
import com.floworchestrator.mcpdomain.remotos.VehicleCatalogRemoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleDomainServiceTest {

    @Mock
    private VehicleCatalogRemoteRepository vehicleCatalogRemoteRepository;

    @Mock
    private PurchaseRemoteRepository purchaseRemoteRepository;

    @Mock
    private PaymentMethodRemoteRepository paymentMethodRemoteRepository;

    private VehicleDomainService service;

    @BeforeEach
    void setUp() {
        service = new VehicleDomainService(
                vehicleCatalogRemoteRepository,
                purchaseRemoteRepository,
                paymentMethodRemoteRepository,
                new IdempotencyStore());
    }

    @Test
    void returnsCatalogDataForKnownVehicle() {
        when(vehicleCatalogRemoteRepository.getVehicleById("v-101"))
                .thenReturn(Map.of("brand", "Toyota", "price", 250000));

        Map<String, Object> result = service.getVehicleInformation("v-101");

        assertThat(result)
                .containsEntry("vehicle_id", "v-101")
                .containsEntry("brand", "Toyota")
                .containsEntry("price", 250000);
    }

    @Test
    void unknownVehicleThrowsNotFound() {
        when(vehicleCatalogRemoteRepository.getVehicleById("v-404")).thenReturn(Map.of());

        assertThatThrownBy(() -> service.getVehicleInformation("v-404"))
                .isInstanceOf(VehicleNotFoundException.class)
                .hasMessageContaining("v-404");
    }

    @Test
    void nullCatalogResponseThrowsNotFound() {
        when(vehicleCatalogRemoteRepository.getVehicleById("v-404")).thenReturn(null);

        assertThatThrownBy(() -> service.getVehicleInformation("v-404"))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void blankVehicleIdIsRejectedBeforeCallingTheCatalog() {
        assertThatThrownBy(() -> service.getVehicleInformation("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vehicleId");
    }

    @Test
    void registersPurchaseWithDeterministicIdentifier() {
        when(purchaseRemoteRepository.createPurchaseOrder(anyMap())).thenReturn(Map.of());

        Map<String, Object> result = service.registerVehiclePurchase("c-1", "v-1", "a-1", null);

        assertThat(result)
                .containsEntry("status", "CREATED")
                .containsEntry("customer_id", "c-1")
                .containsEntry("sales_agent_id", "a-1")
                .containsEntry("idempotent_replay", false);
        assertThat(result.get("purchase_order_id").toString()).startsWith("po-");
    }

    @Test
    void retryWithSameArgumentsReplaysOriginalPurchaseOrder() {
        when(purchaseRemoteRepository.createPurchaseOrder(anyMap())).thenReturn(Map.of());

        Map<String, Object> first = service.registerVehiclePurchase("c-1", "v-1", "a-1", null);
        Map<String, Object> second = service.registerVehiclePurchase("c-1", "v-1", "a-1", null);

        assertThat(second)
                .containsEntry("purchase_order_id", first.get("purchase_order_id"))
                .containsEntry("idempotent_replay", true);
        verify(purchaseRemoteRepository, times(1)).createPurchaseOrder(anyMap());
    }

    @Test
    void explicitIdempotencyKeysCreateSeparateOrders() {
        when(purchaseRemoteRepository.createPurchaseOrder(anyMap())).thenReturn(Map.of());

        Map<String, Object> first = service.registerVehiclePurchase("c-1", "v-1", "a-1", "key-1");
        Map<String, Object> second = service.registerVehiclePurchase("c-1", "v-1", "a-1", "key-2");

        assertThat(first.get("purchase_order_id")).isNotEqualTo(second.get("purchase_order_id"));
        verify(purchaseRemoteRepository, times(2)).createPurchaseOrder(anyMap());
    }

    @Test
    void mergesDownstreamPurchaseResponse() {
        when(purchaseRemoteRepository.createPurchaseOrder(anyMap()))
                .thenReturn(Map.of("remote_reference", "R-1"));

        Map<String, Object> result = service.registerVehiclePurchase("c-1", "v-1", "a-1", null);

        assertThat(result).containsEntry("remote_reference", "R-1");
    }

    @Test
    void rejectsMissingPurchaseIdentifiers() {
        assertThatThrownBy(() -> service.registerVehiclePurchase(" ", "v-1", "a-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerId");
        assertThatThrownBy(() -> service.registerVehiclePurchase("c-1", null, "a-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vehicleId");
        assertThatThrownBy(() -> service.registerVehiclePurchase("c-1", "v-1", "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salesAgentId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void appliesPaymentDefaults() {
        when(paymentMethodRemoteRepository.registerPayment(anyMap())).thenReturn(Map.of());

        Map<String, Object> result = service.setPaymentMethod("po-1", null, null, null);

        assertThat(result)
                .containsEntry("status", "PAYMENT_CONFIGURED")
                .containsEntry("payment_type", PaymentDefaults.PAYMENT_TYPE)
                .containsEntry("financial_institution", PaymentDefaults.FINANCIAL_INSTITUTION)
                .containsEntry("down_payment_amount", PaymentDefaults.DOWN_PAYMENT_AMOUNT);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(paymentMethodRemoteRepository).registerPayment(captor.capture());
        assertThat(captor.getValue())
                .containsEntry("payment_type", PaymentDefaults.PAYMENT_TYPE)
                .containsEntry("down_payment_amount", PaymentDefaults.DOWN_PAYMENT_AMOUNT);
    }

    @Test
    void acceptsExplicitPaymentConfiguration() {
        when(paymentMethodRemoteRepository.registerPayment(anyMap())).thenReturn(Map.of());

        Map<String, Object> result = service.setPaymentMethod("po-1", "CASH", "Banorte", 20000.5);

        assertThat(result)
                .containsEntry("payment_type", "CASH")
                .containsEntry("financial_institution", "Banorte")
                .containsEntry("down_payment_amount", 20000.5);
    }

    @Test
    void retryWithSamePaymentValuesReplaysResult() {
        when(paymentMethodRemoteRepository.registerPayment(anyMap())).thenReturn(Map.of());

        service.setPaymentMethod("po-1", "FINANCING", "BBVA", 15000);
        Map<String, Object> second = service.setPaymentMethod("po-1", "FINANCING", "BBVA", 15000);

        assertThat(second).containsEntry("idempotent_replay", true);
        verify(paymentMethodRemoteRepository, times(1)).registerPayment(anyMap());
    }

    @Test
    void changedPaymentValuesAreRegisteredAgain() {
        when(paymentMethodRemoteRepository.registerPayment(anyMap())).thenReturn(Map.of());

        service.setPaymentMethod("po-1", "FINANCING", "BBVA", 15000);
        service.setPaymentMethod("po-1", "FINANCING", "BBVA", 25000);

        verify(paymentMethodRemoteRepository, times(2)).registerPayment(anyMap());
    }

    @Test
    void rejectsBlankPurchaseOrderAndNegativeDownPayment() {
        assertThatThrownBy(() -> service.setPaymentMethod(" ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("purchaseOrderId");
        assertThatThrownBy(() -> service.setPaymentMethod("po-1", null, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("downPaymentAmount");
    }

    @Test
    void mergesDownstreamPaymentResponse() {
        when(paymentMethodRemoteRepository.registerPayment(anyMap()))
                .thenReturn(Map.of("remote_reference", "P-1"));

        Map<String, Object> result = service.setPaymentMethod("po-1", null, null, null);

        assertThat(result).containsEntry("remote_reference", "P-1");
    }
}
