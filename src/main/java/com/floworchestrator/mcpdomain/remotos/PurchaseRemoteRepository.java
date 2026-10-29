package com.floworchestrator.mcpdomain.remotos;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Cliente Feign de órdenes de compra del servicio de dominio de vehículos; resuelve
 * la URL base desde {@code services.auto-database.base-url}.
 */
@FeignClient(name = "purchase-service", url = "${services.auto-database.base-url:http://localhost:8083}")
public interface PurchaseRemoteRepository {

    /**
     * Llama a {@code POST /purchase-orders} para crear la orden; un HTTP 409 indica que ya existía.
     */
    @PostMapping("/purchase-orders")
    Map<String, Object> createPurchaseOrder(@RequestBody Map<String, Object> payload);
}
