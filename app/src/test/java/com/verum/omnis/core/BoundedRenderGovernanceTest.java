package com.verum.omnis.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class BoundedRenderGovernanceTest {

    @Test
    public void boundedRenderSettingsDefaultToActivationSafeValues() {
        BoundedRenderSettings settings = new BoundedRenderSettings();

        assertFalse(settings.getBoundedHumanBriefEnabled());
        assertFalse(settings.getBoundedPoliceSummaryEnabled());
        assertFalse(settings.getBoundedLegalStandingEnabled());
        assertTrue(settings.getBoundedRenderAuditRequired());
        assertTrue(settings.getBoundedRenderFailClosed());
    }

    @Test
    public void modelAuditLedgerIsEmptyWhenNoBoundedRunWasLogged() {
        String ledgerJson = ModelAuditLogger.buildLedgerJson(null, "case-none");

        assertTrue(ledgerJson.isEmpty());
    }

    @Test
    public void modelAuditEntriesRoundTripThroughJson() throws Exception {
        ModelAuditLogEntry entry = ModelAuditLogger.buildEntry(
                "case-audit",
                "gemma-4",
                "REPORT_RENDER_HUMAN_BRIEF",
                "{\"input\":true}",
                "{\"report\":\"ok\"}",
                true,
                12L,
                "HUMAN_BRIEF_V1",
                false,
                java.util.Collections.singletonList("bounded")
        );

        ModelAuditLogEntry parsed = ModelAuditLogEntry.fromJson(new JSONObject(entry.toJson().toString()));

        assertTrue(parsed.getSuccess());
        assertTrue(parsed.getInputHash() != null && !parsed.getInputHash().isEmpty());
        assertTrue(parsed.getOutputHash() != null && !parsed.getOutputHash().isEmpty());
        assertTrue(parsed.getNotes().contains("bounded"));
    }
}
