package com.floworchestrator.mcpdomain.remotos;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "auto-db-service", url = "${services.auto-database.base-url:http://localhost:8083}")
public interface VehicleCatalogRemoteRepository {

    @GetMapping("/vehicles/{vehicleId}")
    Map<String, Object> getVehicleById(@PathVariable("vehicleId") String vehicleId);
}
