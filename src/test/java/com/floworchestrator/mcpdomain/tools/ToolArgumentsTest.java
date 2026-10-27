package com.floworchestrator.mcpdomain.tools;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ToolArgumentsTest {

    private static final String TOOL = "test_tool";

    @Test
    void requireStringRejectsMissingBlankAndNonStringValues() {
        assertThat(toolException(() -> ToolArguments.requireString(Map.of(), "vehicle_id", TOOL)).errorCode())
                .isEqualTo("MISSING_ARGUMENT");

        assertThat(toolException(() ->
                ToolArguments.requireString(Map.of("vehicle_id", "   "), "vehicle_id", TOOL)).errorCode())
                .isEqualTo("MISSING_ARGUMENT");

        assertThat(toolException(() ->
                ToolArguments.requireString(Map.of("vehicle_id", 10), "vehicle_id", TOOL)).errorCode())
                .isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void requireStringTrimsAndRejectsOverlongIdentifiers() {
        assertThat(ToolArguments.requireString(Map.of("customer_id", "  c-1 "), "customer_id", TOOL))
                .isEqualTo("c-1");

        ToolException exception = toolException(() ->
                ToolArguments.requireString(Map.of("customer_id", "x".repeat(129)), "customer_id", TOOL));
        assertThat(exception.getMessage()).contains("máximo 128");
    }

    @Test
    void missingArgumentMessageTellsTheModelHowToRecover() {
        ToolException exception = toolException(() ->
                ToolArguments.requireString(Map.of(), "purchase_order_id", TOOL));

        assertThat(exception.getMessage()).contains("purchase_order_id");
        assertThat(exception.howToFix())
                .contains("Solicita al usuario")
                .contains("purchase_order_id")
                .contains("No inventes un valor");
        assertThat(exception.retryable()).isFalse();
    }

    @Test
    void optionalStringFallsBackToDefaultForMissingBlankOrNull() {
        assertThat(ToolArguments.optionalString(Map.of(), "payment_type", "FINANCING", TOOL))
                .isEqualTo("FINANCING");
        assertThat(ToolArguments.optionalString(Map.of("payment_type", "  "), "payment_type", "FINANCING", TOOL))
                .isEqualTo("FINANCING");
        assertThat(ToolArguments.optionalString(
                Collections.singletonMap("payment_type", null), "payment_type", "FINANCING", TOOL))
                .isEqualTo("FINANCING");
    }

    @Test
    void optionalStringRejectsNonStringValues() {
        ToolException exception = toolException(() -> ToolArguments.optionalString(
                Map.of("financial_institution", 55), "financial_institution", "BBVA", TOOL));

        assertThat(exception.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(exception.field()).isEqualTo("financial_institution");
    }

    @Test
    void optionalNumberAcceptsFiniteNonNegativeNumbers() {
        assertThat(ToolArguments.optionalNumber(Map.of(), "down_payment_amount", 15000, TOOL))
                .isEqualTo(15000);
        assertThat(ToolArguments.optionalNumber(
                Map.of("down_payment_amount", 20000.5), "down_payment_amount", 15000, TOOL))
                .isEqualTo(20000.5);
    }

    @Test
    void optionalNumberRejectsNegativeNonFiniteAndNonNumericValues() {
        assertThat(toolException(() -> ToolArguments.optionalNumber(
                Map.of("down_payment_amount", -1), "down_payment_amount", 15000, TOOL)).getMessage())
                .contains("down_payment_amount");

        assertThat(toolException(() -> ToolArguments.optionalNumber(
                Map.of("down_payment_amount", Double.NaN), "down_payment_amount", 15000, TOOL)).errorCode())
                .isEqualTo("INVALID_ARGUMENT");

        assertThat(toolException(() -> ToolArguments.optionalNumber(
                Map.of("down_payment_amount", "20000"), "down_payment_amount", 15000, TOOL)).errorCode())
                .isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void optionalIdempotencyKeyValidatesLength() {
        assertThat(ToolArguments.optionalIdempotencyKey(Map.of(), TOOL)).isNull();
        assertThat(ToolArguments.optionalIdempotencyKey(Map.of("idempotency_key", " key-1 "), TOOL))
                .isEqualTo("key-1");

        ToolException exception = toolException(() ->
                ToolArguments.optionalIdempotencyKey(Map.of("idempotency_key", "k".repeat(129)), TOOL));
        assertThat(exception.getMessage()).contains("idempotency_key");
    }

    private static ToolException toolException(Runnable action) {
        Throwable throwable = catchThrowable(action::run);
        assertThat(throwable).isInstanceOf(ToolException.class);
        return (ToolException) throwable;
    }
}
