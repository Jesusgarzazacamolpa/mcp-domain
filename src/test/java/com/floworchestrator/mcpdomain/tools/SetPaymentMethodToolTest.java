package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.PaymentDefaults;
import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetPaymentMethodToolTest {

    @Mock
    private VehicleDomainService vehicleDomainService;

    @InjectMocks
    private SetPaymentMethodTool tool;

    @Test
    void appliesDefaultsWhenOptionalArgumentsAreOmitted() {
        when(vehicleDomainService.setPaymentMethod("po-1", PaymentDefaults.PAYMENT_TYPE,
                PaymentDefaults.FINANCIAL_INSTITUTION, PaymentDefaults.DOWN_PAYMENT_AMOUNT))
                .thenReturn(Map.of("purchase_order_id", "po-1", "status", "PAYMENT_CONFIGURED"));

        ToolResponse response = tool.execute(Map.of("purchase_order_id", "po-1"));

        assertThat(response.error()).isFalse();
        verify(vehicleDomainService).setPaymentMethod("po-1", PaymentDefaults.PAYMENT_TYPE,
                PaymentDefaults.FINANCIAL_INSTITUTION, PaymentDefaults.DOWN_PAYMENT_AMOUNT);
    }

    @Test
    void acceptsExplicitPaymentConfiguration() {
        when(vehicleDomainService.setPaymentMethod("po-1", "CASH", "Banorte", 20000.5))
                .thenReturn(Map.of("purchase_order_id", "po-1"));

        ToolResponse response = tool.execute(Map.of(
                "purchase_order_id", "po-1",
                "payment_type", "CASH",
                "financial_institution", "Banorte",
                "down_payment_amount", 20000.5));

        assertThat(response.error()).isFalse();
        verify(vehicleDomainService).setPaymentMethod("po-1", "CASH", "Banorte", 20000.5);
    }

    @Test
    void blankOptionalStringsFallBackToDefaults() {
        when(vehicleDomainService.setPaymentMethod("po-1", PaymentDefaults.PAYMENT_TYPE,
                PaymentDefaults.FINANCIAL_INSTITUTION, PaymentDefaults.DOWN_PAYMENT_AMOUNT))
                .thenReturn(Map.of("purchase_order_id", "po-1"));

        tool.execute(Map.of("purchase_order_id", "po-1", "payment_type", " ", "financial_institution", ""));

        verify(vehicleDomainService).setPaymentMethod("po-1", PaymentDefaults.PAYMENT_TYPE,
                PaymentDefaults.FINANCIAL_INSTITUTION, PaymentDefaults.DOWN_PAYMENT_AMOUNT);
    }

    @Test
    void missingPurchaseOrderIdReturnsCorrectiveMessage() {
        ToolResponse response = tool.execute(Map.of());

        assertThat(response.payload())
                .containsEntry("error_code", "MISSING_ARGUMENT")
                .containsEntry("field", "purchase_order_id");
        assertThat(response.payload().get("how_to_fix").toString())
                .contains("register_vehicle_purchase");
    }

    @Test
    void negativeDownPaymentIsRejected() {
        ToolResponse response = tool.execute(Map.of("purchase_order_id", "po-1", "down_payment_amount", -5));

        assertThat(response.payload())
                .containsEntry("error_code", "INVALID_ARGUMENT")
                .containsEntry("field", "down_payment_amount");
    }

    @Test
    void nonNumericDownPaymentIsRejectedWithInstruction() {
        ToolResponse response = tool.execute(
                Map.of("purchase_order_id", "po-1", "down_payment_amount", "5000"));

        assertThat(response.payload()).containsEntry("error_code", "INVALID_ARGUMENT");
        assertThat(response.payload().get("how_to_fix").toString()).contains("número JSON");
    }

    @Test
    void downstreamFailureIsContained() {
        FeignException failure = mock(FeignException.class);
        when(failure.status()).thenReturn(502);
        when(vehicleDomainService.setPaymentMethod("po-1", PaymentDefaults.PAYMENT_TYPE,
                PaymentDefaults.FINANCIAL_INSTITUTION, PaymentDefaults.DOWN_PAYMENT_AMOUNT))
                .thenThrow(failure);

        ToolResponse response = tool.execute(Map.of("purchase_order_id", "po-1"));

        assertThat(response.payload())
                .containsEntry("error_code", "DEPENDENCY_UNAVAILABLE")
                .containsEntry("retryable", true);
    }

    @Test
    void nullArgumentsAreTreatedAsEmpty() {
        ToolResponse response = tool.execute(null);

        assertThat(response.error()).isTrue();
        assertThat(response.payload()).containsEntry("error_code", "MISSING_ARGUMENT");
    }
}
