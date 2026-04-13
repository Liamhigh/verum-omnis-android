package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class FindingPublicationNormalizerTest {

    @Test
    public void normalizeCertifiedFindingsCollapsesActorAliasesBeforePublication() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.guardianDecision = new JSONObject().put("approved", true);
        report.certifiedFindings = new JSONArray()
                .put(certifiedFinding("Des", "Lease renewal was not countersigned or returned.", new int[] {28, 48}))
                .put(certifiedFinding("Desmond Smith", "Lease renewal was not countersigned or returned.", new int[] {28, 48}));

        JSONArray normalized = FindingPublicationNormalizer.normalizeCertifiedFindings(report);

        assertEquals(1, normalized.length());
        assertEquals("Desmond Smith", normalized.getJSONObject(0).optString("actor"));
    }

    private JSONObject certifiedFinding(String actor, String summary, int[] pages) throws Exception {
        JSONArray anchors = new JSONArray();
        for (int page : pages) {
            anchors.put(new JSONObject().put("page", page));
        }
        JSONObject finding = new JSONObject()
                .put("actor", actor)
                .put("summary", summary)
                .put("anchors", anchors);
        return new JSONObject()
                .put("actor", actor)
                .put("summary", summary)
                .put("anchors", anchors)
                .put("finding", finding)
                .put("certification", new JSONObject().put("guardianApproval", true));
    }
}
