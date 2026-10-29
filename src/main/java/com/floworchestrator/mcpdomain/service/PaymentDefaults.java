package com.floworchestrator.mcpdomain.service;

/**
 * Valores por defecto que aplica {@code set_payment_method} cuando el modelo omite los
 * argumentos opcionales; también se publican en el recurso MCP {@code payment-defaults}.
 */
public final class PaymentDefaults {

    public static final String PAYMENT_TYPE = "FINANCING";
    public static final String FINANCIAL_INSTITUTION = "BBVA";
    public static final int DOWN_PAYMENT_AMOUNT = 15000;

    private PaymentDefaults() {
    }
}
