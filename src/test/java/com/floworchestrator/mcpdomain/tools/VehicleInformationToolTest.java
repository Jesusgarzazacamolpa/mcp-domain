package com.floworchestrator.mcpdomain.tools;

import com.floworchestrator.mcpdomain.service.VehicleDomainService;
import com.floworchestrator.mcpdomain.service.VehicleNotFoundException;
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
class VehicleInformationToolTest {

    @Mock
    private VehicleDomainService vehicleDomainService;

    @InjectMocks
    private VehicleInformationTool tool;

    @Test
    void returnsVehicleDataForValidArguments() {
        when(vehicleDomainService.getVehicleInformation("v-101"))
                .thenReturn(Map.of("vehicle_id", "v-101", "brand", "Toyota"));

        ToolResponse response = tool.execute(Map.of("vehicle_id", "v-101"));

        assertThat(response.error()).isFalse();
        assertThat(response.payload()).containsEntry("brand", "Toyota");
        verify(vehicleDomainService).getVehicleInformation("v-101");
    }

    @Test
    void missingVehicleIdReturnsCorrectiveMessageInsteadOfThrowing() {
        ToolResponse response = tool.execute(Map.of());

        assertThat(response.error()).isTrue();
        assertThat(response.payload())
                .containsEntry("error_code", "MISSING_ARGUMENT")
                .containsEntry("field", "vehicle_id")
                .containsEntry("retryable", false);
        assertThat(response.payload().get("how_to_fix").toString())
                .contains("Solicita al usuario")
                .contains("No inventes un valor");
    }

    @Test
    void blankAndNonStringVehicleIdsAreRejected() {
        assertThat(tool.execute(Map.of("vehicle_id", "  ")).payload())
                .containsEntry("error_code", "MISSING_ARGUMENT");
        assertThat(tool.execute(Map.of("vehicle_id", 7)).payload())
                .containsEntry("error_code", "INVALID_ARGUMENT");
    }

    @Test
    void nullArgumentsAreTreatedAsEmpty() {
        ToolResponse response = tool.execute(null);

        assertThat(response.error()).isTrue();
        assertThat(response.payload()).containsEntry("error_code", "MISSING_ARGUMENT");
    }

    @Test
    void unknownVehicleReturnsNotFoundCorrection() {
        when(vehicleDomainService.getVehicleInformation("v-404"))
                .thenThrow(new VehicleNotFoundException("v-404"));

        ToolResponse response = tool.execute(Map.of("vehicle_id", "v-404"));

        assertThat(response.payload())
                .containsEntry("error_code", "VEHICLE_NOT_FOUND")
                .containsEntry("retryable", false);
        assertThat(response.payload().get("how_to_fix").toString()).contains("confirme el identificador del vehículo");
    }

    @Test
    void downstreamNotFoundIsMappedToResourceNotFound() {
        when(vehicleDomainService.getVehicleInformation("v-1"))
                .thenThrow(mock(FeignException.NotFound.class));

        ToolResponse response = tool.execute(Map.of("vehicle_id", "v-1"));

        assertThat(response.payload()).containsEntry("error_code", "RESOURCE_NOT_FOUND");
    }

    @Test
    void downstreamFailureIsRetryableAndExplained() {
        FeignException failure = mock(FeignException.class);
        when(failure.status()).thenReturn(503);
        when(vehicleDomainService.getVehicleInformation("v-1")).thenThrow(failure);

        ToolResponse response = tool.execute(Map.of("vehicle_id", "v-1"));

        assertThat(response.payload())
                .containsEntry("error_code", "DEPENDENCY_UNAVAILABLE")
                .containsEntry("retryable", true);
        assertThat(response.payload().get("message").toString()).contains("503");
    }

    @Test
    void illegalArgumentIsMappedToInvalidArgument() {
        when(vehicleDomainService.getVehicleInformation("v-1"))
                .thenThrow(new IllegalArgumentException("vehicleId is required"));

        ToolResponse response = tool.execute(Map.of("vehicle_id", "v-1"));

        assertThat(response.payload()).containsEntry("error_code", "INVALID_ARGUMENT");
    }

    @Test
    void unexpectedFailureIsContainedAndNotRethrown() {
        when(vehicleDomainService.getVehicleInformation("v-1"))
                .thenThrow(new IllegalStateException("boom"));

        ToolResponse response = tool.execute(Map.of("vehicle_id", "v-1"));

        assertThat(response.payload())
                .containsEntry("error_code", "INTERNAL_ERROR")
                .containsEntry("retryable", false);
    }
}
