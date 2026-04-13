package com.verum.omnis.core;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PoliceReadyReportBuilderTest {

    @Test
    public void renderBuildsPoliceReadySectionsWithChargeLanguageAndPages() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.caseId = "case-test";
        report.evidenceHash = "abc123";
        report.jurisdiction = "ZA";
        report.jurisdictionName = "South Africa";
        report.engineVersion = "engine-test";
        report.deterministicRunId = "run-test";
        report.generatedAt = "2026-04-11T06:00:00Z";
        report.diagnostics = new JSONObject().put("processingStatus", "DETERMINATE");
        report.tripleVerification = new JSONObject()
                .put("overall", new JSONObject().put("status", "PASS"))
                .put("antithesis", new JSONObject().put("verifiedCount", 0).put("candidateCount", 1));
        report.legalReferences = new String[] {"PRECCA Section 34"};

        JSONObject conclusion = new JSONObject()
                .put("publicationBoundary", "The engine can state what happened and who is materially implicated, but cannot yet publish a final guilt verdict in this pass.")
                .put("stronglyAllegedExposure", new JSONArray().put("concealment"))
                .put("proofGaps", new JSONArray().put("A verified paired conflict is still missing."))
                .put("implicatedActors", new JSONArray()
                        .put(new JSONObject()
                                .put("actor", "All Fuels")
                                .put("role", "PRIMARY_IMPLICATED")
                                .put("basis", new JSONArray().put("Certified conduct anchors the pressure pattern."))
                                .put("anchorPages", new JSONArray().put(128).put(129)))
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
        report.forensicConclusion = conclusion;

        ForensicReportAssembler.Assembly assembled = new ForensicReportAssembler.Assembly();
        assembled.guardianApprovedCertifiedFindingCount = 3;
        assembled.verifiedContradictionCount = 0;
        assembled.candidateContradictionCount = 1;
        assembled.contradictionPosture = "The contradiction layer has not yet matured into a verified paired conflict.";
        assembled.readFirstPages = Arrays.asList("128", "129", "130");
        assembled.issueGroups = Collections.singletonList(
                new ForensicReportAssembler.IssueCard(
                        "Eviction-pressure pattern",
                        "All Fuels is linked to non-renewal, vacate, or eviction-pressure events.",
                        "It anchors the main conduct pattern.",
                        Collections.singletonList("All Fuels"),
                        Arrays.asList(128, 129, 130, 133),
                        "CERTIFIED",
                        Collections.emptyList()
                )
        );
        assembled.chronology = Collections.singletonList(
                new ForensicReportAssembler.ChronologyEvent(
                        "31 Dec",
                        "non-renewal, vacate, or eviction-pressure events",
                        Collections.singletonList("All Fuels"),
                        Arrays.asList(128, 129, 130, 133),
                        "ANCHORED"
                )
        );

        String rendered = PoliceReadyReportBuilder.render(report, assembled, "greensky.zip", "UTC 2026-04-11T06:00:00Z");

        assertTrue(rendered.startsWith("VERUM OMNIS - POLICE READY CONSTITUTIONAL FORENSIC REPORT"));
        assertTrue(rendered.contains("SECTION 6 — RECOMMENDED CHARGES FOR DOCKET CONSIDERATION"));
        assertTrue(rendered.contains("SECTION 7 — PERSONS TO BE CHARGED OR FORMALLY INVESTIGATED"));
        assertTrue(rendered.contains("Recommended status: CHARGE"));
        assertTrue(rendered.contains("Pages 128, 129, 130, 133") || rendered.contains("128, 129, 130, 133"));
        assertTrue(rendered.contains("This police-ready constitutional report states what the sealed record shows"));
    }

    @Test
    public void renderCleansMachineFragmentsFromExecutiveSummaryAndChronology() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.caseId = "case-test";
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
        report.forensicConclusion = new JSONObject()
                .put("publicationBoundary", "This is a forensic conclusion, not a judicial verdict.")
                .put("whatHappened", new JSONArray().put("The sealed record shows a repeated pattern in which papers were advanced, no countersigned copy returned, money continued to move, and pressure to vacate followed."))
                .put("forensicPropositions", new JSONArray()
                        .put(new JSONObject()
                                .put("actor", "All Fuels")
                                .put("conduct", "non-renewal, vacate, or eviction-pressure events")
                                .put("anchorPages", new JSONArray().put(128).put(129).put(130).put(133))));

        ForensicReportAssembler.Assembly assembled = new ForensicReportAssembler.Assembly();
        assembled.guardianApprovedCertifiedFindingCount = 3;
        assembled.verifiedContradictionCount = 0;
        assembled.candidateContradictionCount = 1;
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
        assembled.chronology = Arrays.asList(
                new ForensicReportAssembler.ChronologyEvent(
                        "00 MARCH",
                        "On 00 MARCH, CLIENT VIA is linked to dated client or invoice correspondence.",
                        Collections.singletonList("CLIENT VIA"),
                        Collections.singletonList(1),
                        "ANCHORED"
                ),
                new ForensicReportAssembler.ChronologyEvent(
                        "31 July",
                        "On 31 July, Desmond Smith is linked to an eviction or vacate-related statement.",
                        Collections.singletonList("Desmond Smith"),
                        Collections.singletonList(48),
                        "ANCHORED"
                )
        );

        String rendered = PoliceReadyReportBuilder.render(report, assembled, "Combine 06 April 2026.PDF", "UTC 2026-04-11T06:00:00Z");

        assertTrue(rendered, rendered.contains("The sealed record shows a repeated pattern in which papers were advanced, no countersigned copy returned, money continued to move, and pressure to vacate followed."));
        assertTrue(rendered, rendered.contains("On or about 31 July, Desmond Smith is linked in the sealed record to an eviction or vacate-related statement."));
        assertFalse(rendered, rendered.contains("CLIENT VIA"));
        assertFalse(rendered, rendered.contains("00 MARCH"));
        assertFalse(rendered, rendered.contains("dated client or invoice correspondence"));
    }

    @Test
    public void renderDoesNotPromoteAffectedPartyAliasIntoChargeRow() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.caseId = "case-test";
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
        report.forensicConclusion = new JSONObject()
                .put("publicationBoundary", "This is a forensic conclusion, not a judicial verdict.")
                .put("implicatedActors", new JSONArray()
                        .put(new JSONObject()
                                .put("actor", "All Fuels")
                                .put("role", "PRIMARY_IMPLICATED")
                                .put("basis", new JSONArray().put("All Fuels anchors the pressure pattern."))
                                .put("anchorPages", new JSONArray().put(128).put(129).put(130).put(133)))
                        .put(new JSONObject()
                                .put("actor", "Desmond Smith")
                                .put("role", "AFFECTED_PARTY")
                                .put("basis", new JSONArray().put("Desmond Smith is the harmed-side anchor in the document-execution material."))
                                .put("anchorPages", new JSONArray().put(28).put(48))))
                .put("forensicPropositions", new JSONArray()
                        .put(new JSONObject()
                                .put("actor", "All Fuels")
                                .put("conduct", "non-renewal, vacate, or eviction-pressure events")
                                .put("anchorPages", new JSONArray().put(128).put(129).put(130).put(133))
                                .put("offenceMapping", "fraud")
                                .put("status", "CERTIFIED_CONDUCT"))
                        .put(new JSONObject()
                                .put("actor", "Des")
                                .put("conduct", "a lease, MOU, or renewal was not countersigned or returned")
                                .put("anchorPages", new JSONArray().put(28).put(48))
                                .put("status", "CERTIFIED_CONDUCT")));

        ForensicReportAssembler.Assembly assembled = new ForensicReportAssembler.Assembly();
        assembled.guardianApprovedCertifiedFindingCount = 3;
        assembled.verifiedContradictionCount = 0;
        assembled.candidateContradictionCount = 1;
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

        assertFalse(rendered, rendered.contains("Respondent(s) to be investigated/charged: Desmond Smith"));
        assertFalse(rendered, rendered.contains("Respondent(s) to be investigated/charged: Des"));
        assertTrue(rendered, rendered.contains("- Full name / entity name: Desmond Smith"));
        assertTrue(rendered, rendered.contains("Recommended status: TAKE STATEMENT ONLY"));
    }
}
