package com.floworchestrator.mcpdomain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.floworchestrator.mcpdomain.remotos")
public class McpDomainServer {
    public static void main(String[] args) {
        SpringApplication.run(McpDomainServer.class, args);
    }
}
