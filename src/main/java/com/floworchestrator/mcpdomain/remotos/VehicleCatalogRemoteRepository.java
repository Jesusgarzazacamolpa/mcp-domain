package com.floworchestrator.mcpdomain.remotos;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Cliente Feign del catálogo de vehículos del servicio de dominio de vehículos; resuelve
 * la URL base desde {@code services.auto-database.base-url}.
 */
@FeignClient(name = "auto-db-service", url = "${services.auto-database.base-url:http://localhost:8083}")
public interface VehicleCatalogRemoteRepository {

    /**
     * Llama a {@code GET /vehicles/{vehicleId}}; devuelve el vehículo o {@code null} si no existe.
     */
    @GetMapping("/vehicles/{vehicleId}")
    Map<String, Object> getVehicleById(@PathVariable("vehicleId") String vehicleId);
}
