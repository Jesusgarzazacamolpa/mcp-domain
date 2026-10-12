package com.floworchestrator.mcpdomain.remotos;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "purchase-service", url = "${services.auto-database.base-url:http://localhost:8083}")
public interface PurchaseRemoteRepository {

    @PostMapping("/purchase-orders")
    Map<String, Object> createPurchaseOrder(@RequestBody Map<String, Object> payload);
}
