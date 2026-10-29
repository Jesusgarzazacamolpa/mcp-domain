package com.floworchestrator.mcpdomain.service;

/**
 * Error de dominio que indica que el vehículo solicitado no existe en el catálogo;
 * {@link com.floworchestrator.mcpdomain.tools.ToolErrors} lo traduce a VEHICLE_NOT_FOUND.
 */
public class VehicleNotFoundException extends RuntimeException {

    private final String vehicleId;

    /**
     * Crea la excepción con el identificador de vehículo que no se encontró.
     */
    public VehicleNotFoundException(String vehicleId) {
        super("No se encontró ningún vehículo con el id '" + vehicleId + "'");
        this.vehicleId = vehicleId;
    }

    public String vehicleId() {
        return vehicleId;
    }
}
