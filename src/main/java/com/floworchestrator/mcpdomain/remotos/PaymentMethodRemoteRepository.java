package com.floworchestrator.mcpdomain.remotos;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Cliente Feign del registro de métodos de pago del servicio de dominio de vehículos;
 * resuelve la URL base desde {@code services.auto-database.base-url}.
 */
@FeignClient(name = "payment-service", url = "${services.auto-database.base-url:http://localhost:8083}")
public interface PaymentMethodRemoteRepository {

    /**
     * Llama a {@code POST /payment-methods} para registrar el pago de una orden de compra.
     */
    @PostMapping("/payment-methods")
    Map<String, Object> registerPayment(@RequestBody Map<String, Object> payload);
}
