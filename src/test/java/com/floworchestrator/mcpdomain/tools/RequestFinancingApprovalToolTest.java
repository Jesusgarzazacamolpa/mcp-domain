package com.floworchestrator.mcpdomain.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFinancingApprovalToolTest {

    private final RequestFinancingApprovalTool tool = new RequestFinancingApprovalTool();

    @Test
    void withoutDecisionRequestsHumanIntervention() {
        ToolResponse response = tool.execute(Map.of("purchase_order_id", "po-1"));

        assertThat(response.error()).isFalse();
        assertThat(response.payload())
                .containsEntry("purchase_order_id", "po-1")
                .containsEntry("status", RequestFinancingApprovalTool.STATUS_PENDING)
                .containsEntry("requiresIntervention", true);
        assertThat(response.payload().get("reason").toString()).contains("operador humano");
    }

    @Test
    void approvedDecisionCompletesTheStep() {
        ToolResponse response = tool.execute(Map.of(
                "purchase_order_id", "po-1",
                "decision", "approved"));

        assertThat(response.error()).isFalse();
        assertThat(response.payload())
                .containsEntry("status", RequestFinancingApprovalTool.STATUS_APPROVED)
                .containsEntry("approved", true)
                .doesNotContainKey("requiresIntervention");
    }

    @Test
    void blankDecisionKeepsAskingForHumanIntervention() {
        ToolResponse response = tool.execute(Map.of("purchase_order_id", "po-1", "decision", " "));

        assertThat(response.payload()).containsEntry("requiresIntervention", true);
    }

    @Test
    void rejectsDecisionsOtherThanApproved() {
        ToolResponse response = tool.execute(Map.of("purchase_order_id", "po-1", "decision", "REJECTED"));

        assertThat(response.error()).isTrue();
        assertThat(response.payload())
                .containsEntry("error_code", "INVALID_ARGUMENT")
                .containsEntry("field", "decision");
    }

    @Test
    void missingPurchaseOrderReturnsCorrectiveMessage() {
        ToolResponse response = tool.execute(Map.of());

        assertThat(response.error()).isTrue();
        assertThat(response.payload())
                .containsEntry("error_code", "MISSING_ARGUMENT")
                .containsEntry("field", "purchase_order_id");
        assertThat(response.payload().get("how_to_fix").toString()).contains("register_vehicle_purchase");
    }

    @Test
    void nullArgumentsAreTreatedAsEmpty() {
        ToolResponse response = tool.execute(null);

        assertThat(response.error()).isTrue();
        assertThat(response.payload()).containsEntry("error_code", "MISSING_ARGUMENT");
    }
}
