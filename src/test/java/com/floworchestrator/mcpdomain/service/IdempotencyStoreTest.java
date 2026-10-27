package com.floworchestrator.mcpdomain.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyStoreTest {

    private final IdempotencyStore store = new IdempotencyStore();

    @Test
    void executesActionOnlyOncePerKey() {
        AtomicInteger executions = new AtomicInteger();

        Map<String, Object> first = store.getOrCompute("key", () -> {
            executions.incrementAndGet();
            return Map.of("purchase_order_id", "po-1");
        });
        Map<String, Object> second = store.getOrCompute("key", () -> {
            executions.incrementAndGet();
            return Map.of("purchase_order_id", "po-2");
        });

        assertThat(executions).hasValue(1);
        assertThat(first).containsEntry("idempotent_replay", false);
        assertThat(second)
                .containsEntry("idempotent_replay", true)
                .containsEntry("purchase_order_id", "po-1");
    }

    @Test
    void differentKeysExecuteIndependentActions() {
        AtomicInteger executions = new AtomicInteger();

        store.getOrCompute("key-1", () -> {
            executions.incrementAndGet();
            return Map.of();
        });
        store.getOrCompute("key-2", () -> {
            executions.incrementAndGet();
            return Map.of();
        });

        assertThat(executions).hasValue(2);
    }

    @Test
    void callersCannotMutateTheCachedResult() {
        store.getOrCompute("key", () -> new LinkedHashMap<>(Map.of("value", "original")));

        Map<String, Object> response = store.getOrCompute("key", Map::of);
        response.put("value", "changed");

        assertThat(store.getOrCompute("key", Map::of)).containsEntry("value", "original");
    }
}
