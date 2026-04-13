package com.verum.omnis.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ConstitutionalNarrativePacketBuilderTest {

    @Test
    public void readableBriefPacketContainsDeterministicSourcesOnly() throws Exception {
        AnalysisEngine.ForensicReport report = buildReport();

        String packet = ConstitutionalNarrativePacketBuilder.buildReadableBriefPacket(
                report,
                "greensky.pdf",
                "AUDIT REPORT CONTENT",
                "{\"seed\":\"findings\"}",
                "VISUAL MEMO CONTENT"
        );

        assertTrue(packet.contains("Constitutional Narrative Packet"));
        assertTrue(packet.contains("Truth Frame"));
        assertTrue(packet.contains("Forensic Conclusion"));
        assertTrue(packet.contains("Deterministic Engine Output"));
        assertTrue(packet.contains("AUDIT REPORT CONTENT"));
        assertTrue(packet.contains("VISUAL MEMO CONTENT"));
        assertFalse(packet.contains("LEGAL ATTORNEY ANALYSIS"));
        assertFalse(packet.contains("humanReadableReport"));
        assertFalse(packet.contains("legalAdvisory"));
    }

    private AnalysisEngine.ForensicReport buildReport() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.caseId = "case-packet";
        report.evidenceHashShort = "abc123";
        report.blockchainAnchor = "anchor-1";
        report.summary = "Deterministic summary";
        report.jurisdictionName = "South Africa";
        report.jurisdiction = "ZA";
        report.guardianDecision = new JSONObject().put("approved", true).put("approvedCount", 1);
        report.tripleVerification = new JSONObject()
                .put("thesis", new JSONObject().put("summary", "Thesis summary"))
                .put("antithesis", new JSONObject().put("summary", "Antithesis summary").put("verifiedCount", 0).put("candidateCount", 1))
                .put("synthesis", new JSONObject().put("summary", "Synthesis summary").put("certifiedFindingCount", 1));
        report.forensicSynthesis = new JSONObject()
                .put("victimActors", new JSONArray().put("Marius Nortje"))
                .put("wrongfulActorProfile", new JSONObject().put("actor", "All Fuels").put("verifiedContradictionCount", 0));
        report.diagnostics = new JSONObject()
                .put("processingStatus", "DETERMINATE_WITH_GAPS")
                .put("candidateContradictionCount", 1)
                .put("verifiedContradictionCount", 0);
        report.certifiedFindings = new JSONArray()
                .put(new JSONObject()
                        .put("status", "CANDIDATE")
                        .put("category", "INTER_ACTOR_CONFLICT")
                        .put("actor", "All Fuels")
                        .put("summary", "Quoted admissions and denials conflict on whether the agreement remained active.")
                        .put("page", 128));
        report.forensicConclusion = new JSONObject()
                .put("publicationBoundary", "This is a forensic conclusion, not a judicial verdict.")
                .put("whatHappened", new JSONArray().put("The sealed record shows a repeated pressure pattern."))
                .put("strongestConclusion", "All Fuels is linked in the sealed record to pressure events.");
        return report;
    }
}
