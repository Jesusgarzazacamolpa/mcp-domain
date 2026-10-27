package com.floworchestrator.mcpdomain.service;

public class VehicleNotFoundException extends RuntimeException {

    private final String vehicleId;

    public VehicleNotFoundException(String vehicleId) {
        super("No se encontró ningún vehículo con el id '" + vehicleId + "'");
        this.vehicleId = vehicleId;
    }

    public String vehicleId() {
        return vehicleId;
    }
}
