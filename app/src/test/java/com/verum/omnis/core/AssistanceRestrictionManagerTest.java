package com.verum.omnis.core;

import static org.junit.Assert.assertFalse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Confirms the app-level assistance lockout is dormant so the communicating AIs
 * can operate freely under the Verum Omnis Constitution. The forensic engine's
 * own detection/disclosure of integrity concerns is unaffected.
 */
public class AssistanceRestrictionManagerTest {

    private AnalysisEngine.ForensicReport reportThatWouldTriggerLegacyLockout() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.caseId = "case-test";
        report.evidenceHashShort = "abcd1234";
        report.riskScore = 0.99d;

        JSONObject diagnostics = new JSONObject();
        diagnostics.put("contradictions", 5);
        diagnostics.put("concealment", 5);
        diagnostics.put("evasion", 3);
        diagnostics.put("financial", 2);
        report.diagnostics = diagnostics;

        JSONObject extraction = new JSONObject();
        JSONArray subjects = new JSONArray();
        subjects.put(new JSONObject().put("subject", "Fraudulent Evidence"));
        extraction.put("criticalLegalSubjects", subjects);
        JSONArray incidents = new JSONArray();
        incidents.put(new JSONObject().put("incidentType", "FORGERY").put("description", "forged signature"));
        extraction.put("incidentRegister", incidents);
        extraction.put("namedParties", new JSONArray()); // thin
        extraction.put("anchoredFindings", new JSONArray()); // thin
        report.constitutionalExtraction = extraction;

        return report;
    }

    @Test
    public void lockoutIsDormant() throws Exception {
        AssistanceRestrictionManager.Snapshot snapshot =
                AssistanceRestrictionManager.preview(reportThatWouldTriggerLegacyLockout());
        assertFalse("communicating AIs must not be locked out", snapshot.restricted);
    }
}
