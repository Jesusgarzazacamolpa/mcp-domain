package com.floworchestrator.mcpdomain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Punto de entrada del servicio mcp-domain: arranca Spring Boot y habilita los clientes
 * Feign que consumen las APIs REST del servicio de dominio de vehículos.
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.floworchestrator.mcpdomain.remotos")
public class McpDomainServer {
    /**
     * Inicia la aplicación y deja disponible el endpoint MCP en {@code /mcp}.
     */
    public static void main(String[] args) {
        SpringApplication.run(McpDomainServer.class, args);
    }
}
