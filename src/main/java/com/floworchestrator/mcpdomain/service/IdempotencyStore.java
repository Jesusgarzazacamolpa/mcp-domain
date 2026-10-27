package com.floworchestrator.mcpdomain.service;

import com.floworchestrator.mcpdomain.tools.ToolFlowCodes;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Almacena el resultado de operaciones de escritura para que un reintento con la
 * misma clave devuelva la respuesta original en lugar de duplicar el efecto.
 */
@Component
@Slf4j
public class IdempotencyStore {

    private static final long MAX_ENTRIES = 1_000;
    private static final Duration RETENTION = Duration.ofHours(2);

    private final Cache<String, Map<String, Object>> results = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(RETENTION)
            .build();

    public Map<String, Object> getOrCompute(String key, Supplier<Map<String, Object>> action) {
        AtomicBoolean executed = new AtomicBoolean(false);
        Map<String, Object> stored = results.get(key, ignored -> {
            executed.set(true);
            return Collections.unmodifiableMap(new LinkedHashMap<>(action.get()));
        });
        Map<String, Object> response = new LinkedHashMap<>(stored);
        boolean replayed = !executed.get();
        if (replayed) {
            log.info("[{}] Reintento idempotente detectado; se devuelve el resultado almacenado",
                    ToolFlowCodes.IDEMPOTENCY);
        }
        response.put("idempotent_replay", replayed);
        return response;
    }
}
