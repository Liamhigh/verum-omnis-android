package com.verum.omnis.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ConstitutionalPublicationComplianceTest {

    @Test
    public void readableBriefWithholdsUnanchoredHarmedPartyAndKeepsBoundary() {
        ReadableBriefModel.Input input = new ReadableBriefModel.Input();
        input.caseId = "case-constitutional";
        input.jurisdictionName = "South Africa";
        input.jurisdictionCode = "ZA";
        input.evidenceHashPrefix = "abc123...";
        input.guardianApprovedCertifiedFindingCount = 3;
        input.verifiedContradictionCount = 0;
        input.candidateContradictionCount = 1;
        input.suppressRoleNarration = true;
        input.primaryHarmedParty = "Marius Nortje";
        input.conclusionWhatHappened = "All Fuels is linked in the sealed record to non-renewal, vacate, or eviction-pressure events.";
        input.conclusionPrimaryImplicatedActor = "All Fuels";
        input.conclusionBoundary = "This is a forensic conclusion, not a judicial verdict.";
        input.conclusionPages = Arrays.asList("128", "129", "130", "133");

        String rendered = ReadableBriefBuilder.render(ReadableBriefBuilder.build(input));

        assertTrue(rendered.contains("Verified contradictions: 0"));
        assertTrue(rendered.contains("Candidate contradiction leads: 1"));
        assertTrue(rendered.contains("this pass does not resolve that role cleanly enough to publish it"));
        assertTrue(rendered.contains("not a judicial verdict") || rendered.contains("cannot yet publish a final guilt verdict"));
        assertFalse(rendered.contains("- Harmed party: Marius Nortje"));
        assertFalse(lower(rendered).contains("guilty"));
        assertFalse(lower(rendered).contains("convicted"));
    }

    @Test
    public void contradictionReportStaysContradictionScoped() {
        ContradictionReportModel.Input input = new ContradictionReportModel.Input();
        input.verifiedContradictions = 0;
        input.candidateContradictions = 1;
        input.processingStatus = "DETERMINATE WITH MATERIAL COVERAGE GAPS";
        input.ordinalConfidence = "MODERATE";
        input.executiveSummary = "A candidate contradiction remains open around the agreement and order-completion position.";
        input.contradictionPosture = "The engine can state what happened, but the contradiction layer has not yet matured into a verified paired conflict.";
        input.truthSection = "The current record centers on a contradiction about whether the agreement remained active.";

        String rendered = ContradictionReportBuilder.render(ContradictionReportBuilder.build(input));

        assertTrue(rendered.contains("Verified contradictions: 0"));
        assertTrue(rendered.contains("Candidate contradictions: 1"));
        assertTrue(rendered.contains("does not assign harmed-party labels unless those labels are separately anchored"));
        assertTrue(rendered.contains("It is not a substitute for the sealed evidence pages"));
        assertFalse(lower(rendered).contains("primary implicated actor"));
        assertFalse(lower(rendered).contains("recommended status: charge"));
    }

    @Test
    public void policeReadyReportKeepsChargeLanguageSeparateFromVerdictLanguage() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.caseId = "case-constitutional";
        report.evidenceHash = "abc123";
        report.jurisdiction = "MULTI";
        report.jurisdictionName = "Multi-jurisdiction";
        report.engineVersion = "engine-test";
        report.deterministicRunId = "run-test";
        report.generatedAt = "2026-04-11T06:00:00Z";
        report.diagnostics = new JSONObject().put("processingStatus", "DETERMINATE_WITH_GAPS");
        report.tripleVerification = new JSONObject()
                .put("overall", new JSONObject().put("status", "PASS"))
                .put("antithesis", new JSONObject().put("verifiedCount", 0).put("candidateCount", 1));
        report.legalReferences = new String[] {"PRECCA Section 34", "South African password or access-code misuse framework"};
        report.forensicConclusion = new JSONObject()
                .put("publicationBoundary", "The engine can state what happened and who is materially implicated, but cannot yet publish a final guilt verdict in this pass.")
                .put("whatHappened", new JSONArray().put("The sealed record shows a repeated pattern in which papers were advanced, no countersigned copy returned, money continued to move, and pressure to vacate followed."))
                .put("stronglyAllegedExposure", new JSONArray().put("concealment").put("unauthorized account access or digital interference"))
                .put("proofGaps", new JSONArray().put("A verified paired conflict is still missing."))
                .put("implicatedActors", new JSONArray()
                        .put(new JSONObject()
                                .put("actor", "All Fuels")
                                .put("role", "PRIMARY_IMPLICATED")
                                .put("basis", new JSONArray().put("Certified conduct anchors the pressure pattern."))
                                .put("anchorPages", new JSONArray().put(128).put(129).put(130).put(133)))
                        .put(new JSONObject()
                                .put("actor", "Desmond Smith")
                                .put("role", "AFFECTED_PARTY")
                                .put("basis", new JSONArray().put("The lease or renewal was not countersigned or returned."))
                                .put("anchorPages", new JSONArray().put(28).put(48))))
                .put("forensicPropositions", new JSONArray()
                        .put(new JSONObject()
                                .put("actor", "All Fuels")
                                .put("conduct", "non-renewal, vacate, or eviction-pressure events")
                                .put("timestamp", "UNRESOLVED_TIME")
                                .put("anchorPages", new JSONArray().put(128).put(129).put(130).put(133))
                                .put("offenceMapping", "fraud")
                                .put("status", "CERTIFIED_CONDUCT")
                                .put("publicationBoundary", "This is a forensic conclusion, not a judicial verdict.")));

        ForensicReportAssembler.Assembly assembled = new ForensicReportAssembler.Assembly();
        assembled.guardianApprovedCertifiedFindingCount = 3;
        assembled.verifiedContradictionCount = 0;
        assembled.candidateContradictionCount = 1;
        assembled.contradictionPosture = "The contradiction layer has not yet matured into a verified paired conflict.";
        assembled.readFirstPages = Arrays.asList("128", "129", "130", "133");
        assembled.issueGroups = Collections.singletonList(
                new ForensicReportAssembler.IssueCard(
                        "Document execution and pressure pattern",
                        "The sealed record shows a repeated pattern in which papers were advanced, no countersigned copy returned, money continued to move, and pressure to vacate followed.",
                        "Anchored pattern",
                        Collections.singletonList("All Fuels"),
                        Arrays.asList(128, 129, 130, 133),
                        "HIGH",
                        Collections.emptyList()
                )
        );

        String rendered = PoliceReadyReportBuilder.render(report, assembled, "Combine 06 April 2026.PDF", "UTC 2026-04-11T06:00:00Z");

        assertTrue(rendered.contains("Recommended status: CHARGE"));
        assertTrue(rendered.contains("This is a forensic conclusion, not a judicial verdict."));
        assertTrue(rendered.contains("Candidate contradictions: 1"));
        assertFalse(lower(rendered).contains("is guilty"));
        assertFalse(lower(rendered).contains("committed fraud"));
        assertFalse(lower(rendered).contains("convicted"));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
