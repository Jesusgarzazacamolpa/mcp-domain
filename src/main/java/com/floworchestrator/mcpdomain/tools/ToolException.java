package com.floworchestrator.mcpdomain.tools;

/**
 * Error de validación con instrucciones para que el modelo corrija la llamada.
 */
public class ToolException extends RuntimeException {

    private final String errorCode;
    private final String howToFix;
    private final String field;
    private final boolean retryable;

    private ToolException(
            String errorCode, String message, String howToFix, String field, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.howToFix = howToFix;
        this.field = field;
        this.retryable = retryable;
    }

    /**
     * Crea el error MISSING_ARGUMENT con una pista para que el modelo pida el dato al usuario.
     */
    public static ToolException missingArgument(String toolName, String field, String hint) {
        return new ToolException(
                "MISSING_ARGUMENT",
                "Falta el argumento obligatorio '" + field + "' para la herramienta '" + toolName + "'.",
                "Solicita al usuario " + hint + " y vuelve a llamar a '" + toolName + "' con el campo '" + field
                        + "' establecido. No inventes un valor.",
                field,
                false);
    }

    /**
     * Crea el error INVALID_ARGUMENT indicando el formato esperado del campo.
     */
    public static ToolException invalidArgument(String toolName, String field, String expected) {
        return new ToolException(
                "INVALID_ARGUMENT",
                "El argumento '" + field + "' de la herramienta '" + toolName + "' debe ser " + expected + ".",
                "Envía '" + field + "' como " + expected + ". Si el valor es desconocido, pídelo al usuario "
                        + "y reintenta.",
                field,
                false);
    }

    public String errorCode() {
        return errorCode;
    }

    public String howToFix() {
        return howToFix;
    }

    public String field() {
        return field;
    }

    public boolean retryable() {
        return retryable;
    }
}
