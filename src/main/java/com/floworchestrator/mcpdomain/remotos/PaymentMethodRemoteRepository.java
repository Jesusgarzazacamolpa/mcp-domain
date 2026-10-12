package com.floworchestrator.mcpdomain.remotos;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "payment-service", url = "${services.auto-database.base-url:http://localhost:8083}")
public interface PaymentMethodRemoteRepository {

    @PostMapping("/payment-methods")
    Map<String, Object> registerPayment(@RequestBody Map<String, Object> payload);
}
