package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterVehiclePurchaseToolTest {

    @Mock
    private VehicleDomainService vehicleDomainService;

    @InjectMocks
    private RegisterVehiclePurchaseTool tool;

    @Test
    void registersPurchaseWithRequiredArguments() {
        when(vehicleDomainService.registerVehiclePurchase("c-1", "v-1", "a-1", null))
                .thenReturn(Map.of("purchase_order_id", "po-1", "status", "CREATED"));

        ToolResponse response = tool.execute(Map.of(
                "customer_id", "c-1", "vehicle_id", "v-1", "sales_agent_id", "a-1"));

        assertThat(response.error()).isFalse();
        assertThat(response.payload()).containsEntry("purchase_order_id", "po-1");
        verify(vehicleDomainService).registerVehiclePurchase("c-1", "v-1", "a-1", null);
    }

    @Test
    void passesTrimmedIdempotencyKey() {
        when(vehicleDomainService.registerVehiclePurchase("c-1", "v-1", "a-1", "key-1"))
                .thenReturn(Map.of("purchase_order_id", "po-1"));

        ToolResponse response = tool.execute(Map.of(
                "customer_id", "c-1", "vehicle_id", "v-1", "sales_agent_id", "a-1",
                "idempotency_key", " key-1 "));

        assertThat(response.error()).isFalse();
        verify(vehicleDomainService).registerVehiclePurchase("c-1", "v-1", "a-1", "key-1");
    }

    @Test
    void propagatesReplayFlagFromService() {
        when(vehicleDomainService.registerVehiclePurchase("c-1", "v-1", "a-1", null))
                .thenReturn(Map.of("purchase_order_id", "po-1", "idempotent_replay", true));

        ToolResponse response = tool.execute(Map.of(
                "customer_id", "c-1", "vehicle_id", "v-1", "sales_agent_id", "a-1"));

        assertThat(response.payload()).containsEntry("idempotent_replay", true);
    }

    @Test
    void missingSalesAgentReturnsCorrectiveMessage() {
        ToolResponse response = tool.execute(Map.of("customer_id", "c-1", "vehicle_id", "v-1"));

        assertThat(response.payload())
                .containsEntry("error_code", "MISSING_ARGUMENT")
                .containsEntry("field", "sales_agent_id");
        assertThat(response.payload().get("how_to_fix").toString()).contains("identificador del agente de ventas");
    }

    @Test
    void overlongIdempotencyKeyIsRejected() {
        ToolResponse response = tool.execute(Map.of(
                "customer_id", "c-1", "vehicle_id", "v-1", "sales_agent_id", "a-1",
                "idempotency_key", "k".repeat(129)));

        assertThat(response.payload())
                .containsEntry("error_code", "INVALID_ARGUMENT")
                .containsEntry("field", "idempotency_key");
    }

    @Test
    void nonStringIdempotencyKeyIsRejected() {
        ToolResponse response = tool.execute(Map.of(
                "customer_id", "c-1", "vehicle_id", "v-1", "sales_agent_id", "a-1",
                "idempotency_key", 42));

        assertThat(response.payload()).containsEntry("error_code", "INVALID_ARGUMENT");
    }

    @Test
    void downstreamFailureIsReturnedAsInstruction() {
        FeignException failure = mock(FeignException.class);
        when(failure.status()).thenReturn(500);
        when(vehicleDomainService.registerVehiclePurchase("c-1", "v-1", "a-1", null)).thenThrow(failure);

        ToolResponse response = tool.execute(Map.of(
                "customer_id", "c-1", "vehicle_id", "v-1", "sales_agent_id", "a-1"));

        assertThat(response.payload())
                .containsEntry("error_code", "DEPENDENCY_UNAVAILABLE")
                .containsEntry("retryable", true);
    }

    @Test
    void nullArgumentsReturnMissingArgumentError() {
        ToolResponse response = tool.execute(null);

        assertThat(response.error()).isTrue();
        assertThat(response.payload()).containsEntry("field", "customer_id");
    }

    @Test
    void emptyArgumentsDoNotCallTheService() {
        tool.execute(Map.of());

        verify(vehicleDomainService, never())
                .registerVehiclePurchase(any(), any(), any(), any());
    }
}
