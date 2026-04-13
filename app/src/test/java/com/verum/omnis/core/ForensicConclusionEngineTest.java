package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ForensicConclusionEngineTest {

    @Test
    public void buildPublishesPrimaryImplicatedActorEvenWhenGuiltGateFails() throws Exception {
        AnalysisEngine.ForensicReport report = buildBaseReport();
        ForensicReportAssembler.Assembly assembled = buildBaseAssembly();

        ForensicConclusionEngine.ForensicConclusion conclusion = ForensicConclusionEngine.build(report, assembled);

        assertEquals("IMPLICATION_PATTERN", conclusion.narrativeType);
        assertEquals("DETERMINATE_WITH_GAPS", conclusion.processingStatus);
        assertEquals("All Fuels", ForensicConclusionEngine.primaryImplicatedActorName(conclusion));
        assertFalse(conclusion.guiltGate.allowed);
        assertTrue(conclusion.strongestConclusion.contains("All Fuels"));
        assertTrue(conclusion.strongestConclusion.contains("is linked in the sealed record to"));
        assertFalse(conclusion.strongestConclusion.toLowerCase().contains("may suggest"));
        assertFalse(conclusion.strongestConclusion.toLowerCase().contains("appears to"));
    }

    @Test
    public void buildKeepsBoundaryClearWhenGuiltGateFails() throws Exception {
        AnalysisEngine.ForensicReport report = buildBaseReport();
        ForensicReportAssembler.Assembly assembled = buildBaseAssembly();

        ForensicConclusionEngine.ForensicConclusion conclusion = ForensicConclusionEngine.build(report, assembled);

        assertTrue(conclusion.publicationBoundary.contains("cannot yet publish a final guilt conclusion"));
        assertTrue(conclusion.proofGaps.stream().anyMatch(item -> item.contains("verified paired conflict")));
    }

    @Test
    public void buildAllowsGuiltReadyOnlyWhenGatePasses() throws Exception {
        AnalysisEngine.ForensicReport report = buildBaseReport();
        report.diagnostics.put("verifiedContradictionCount", 2);
        report.diagnostics.put("candidateContradictionCount", 0);
        report.diagnostics.put("indeterminateDueToConcealment", false);
        report.guardianDecision = new JSONObject().put("approved", true);

        JSONObject antithesis = new JSONObject()
                .put("verifiedCount", 2)
                .put("candidateCount", 0);
        report.tripleVerification = new JSONObject().put("antithesis", antithesis);

        ForensicReportAssembler.Assembly assembled = buildBaseAssembly();
        assembled.verifiedContradictionCount = 2;
        assembled.candidateContradictionCount = 0;

        ForensicConclusionEngine.ForensicConclusion conclusion = ForensicConclusionEngine.build(report, assembled);

        assertTrue(conclusion.guiltGate.allowed);
        assertEquals("GUILT_READY", conclusion.narrativeType);
        assertTrue(conclusion.publicationBoundary.contains("met the adjudicative publication gate"));
        assertEquals("CHARGE_READY", conclusion.legalExposure.get(0).status);
    }

    @Test
    public void buildSeparatesCertifiedConductFromExposureAndFrameworkMapping() throws Exception {
        AnalysisEngine.ForensicReport report = buildBaseReport();
        report.topLiabilities = new String[] {
                "Financial Irregularities",
                "Concealment / Deletion",
                "South African password or access-code misuse framework as the leading offence framework"
        };
        report.legalReferences = new String[] {
                "PRECCA Section 34",
                "South African password or access-code misuse framework"
        };

        ForensicReportAssembler.Assembly assembled = buildBaseAssembly();
        ForensicConclusionEngine.ForensicConclusion conclusion = ForensicConclusionEngine.build(report, assembled);

        assertTrue(conclusion.certifiedForensicConduct.stream().anyMatch(item -> item.contains("papers were advanced")));
        assertTrue(conclusion.stronglyAllegedExposure.contains("financial irregularities"));
        assertTrue(conclusion.stronglyAllegedExposure.contains("concealment"));
        assertTrue(conclusion.frameworkMapping.stream().anyMatch(item -> item.toLowerCase().contains("password or access-code misuse framework")));
        assertFalse(conclusion.certifiedForensicConduct.stream().anyMatch(item -> item.toLowerCase().contains("financial irregularities")));
    }

    @Test
    public void buildNormalizesAliasAndKeepsMentionDensityFromWinning() throws Exception {
        AnalysisEngine.ForensicReport report = buildBaseReport();
        report.diagnostics = new JSONObject()
                .put("processingStatus", "DETERMINATE")
                .put("verifiedContradictionCount", 0)
                .put("candidateContradictionCount", 1)
                .put("contradictionRegister", new JSONArray()
                        .put(new JSONObject().put("summary", "Gary Highcock appears in procedural correspondence."))
                        .put(new JSONObject().put("summary", "Gary Highcock appears again in procedural correspondence."))
                        .put(new JSONObject().put("summary", "Gary Highcock appears again in procedural correspondence.")));

        ForensicReportAssembler.Assembly assembled = new ForensicReportAssembler.Assembly();
        assembled.guardianApprovedCertifiedFindingCount = 3;
        assembled.verifiedContradictionCount = 0;
        assembled.candidateContradictionCount = 1;
        assembled.primaryHarmedParty = "Desmond Smith";
        assembled.issueGroups = Arrays.asList(
                new ForensicReportAssembler.IssueCard(
                        "Document execution and pressure pattern",
                        "All Fuels is linked to non-renewal, vacate, or eviction-pressure events.",
                        "It centers the certified conduct pattern.",
                        Collections.singletonList("All Fuels"),
                        Arrays.asList(128, 129, 130, 133),
                        "HIGH",
                        Collections.emptyList()
                ),
                new ForensicReportAssembler.IssueCard(
                        "Document-execution dispute",
                        "Des is linked to primary evidence that a lease, MOU, or renewal was not countersigned or returned.",
                        "It anchors the Desmond Smith-side document execution dispute.",
                        Collections.singletonList("Des"),
                        Arrays.asList(28, 48, 40, 62),
                        "HIGH",
                        Collections.emptyList()
                )
        );
        assembled.directOffenceFindings = Collections.singletonList(
                new CanonicalFindingBridge.DirectOffenceFinding(
                        "fraud",
                        "All Fuels",
                        "The certified conduct pattern anchors the principal implication.",
                        Arrays.asList(128, 129, 130, 133),
                        "HIGH",
                        "test"
                )
        );

        ForensicConclusionEngine.ForensicConclusion conclusion = ForensicConclusionEngine.build(report, assembled);

        assertEquals("All Fuels", ForensicConclusionEngine.primaryImplicatedActorName(conclusion));
        assertTrue(conclusion.implicatedActors.stream().anyMatch(actor -> actor.actor.equals("Desmond Smith")));
        assertFalse(conclusion.implicatedActors.stream().anyMatch(actor -> actor.actor.equals("Des")));
        assertTrue(conclusion.forensicPropositions.stream().anyMatch(item -> item.actor.equals("Desmond Smith")));
        assertFalse(conclusion.forensicPropositions.stream().anyMatch(item -> item.actor.equals("Des")));
    }

    @Test
    public void buildPublishesOnlyBoundedForensicPropositions() throws Exception {
        AnalysisEngine.ForensicReport report = buildBaseReport();
        ForensicReportAssembler.Assembly assembled = buildBaseAssembly();

        ForensicConclusionEngine.ForensicConclusion conclusion = ForensicConclusionEngine.build(report, assembled);

        assertFalse(conclusion.forensicPropositions.isEmpty());
        ForensicConclusionEngine.ForensicProposition first = conclusion.forensicPropositions.get(0);
        assertFalse(first.actor.isEmpty());
        assertFalse(first.conduct.isEmpty());
        assertFalse(first.anchorPages.isEmpty());
        assertEquals("UNRESOLVED_TIME", first.timestamp);
        assertTrue(first.publicationBoundary.contains("forensic conclusion"));
        assertFalse(conclusion.strongestConclusion.toLowerCase().contains("committed"));
        assertFalse(conclusion.strongestConclusion.toLowerCase().contains("guilty"));
    }

    @Test
    public void buildKeepsOffenceMappingSeparateFromConduct() throws Exception {
        AnalysisEngine.ForensicReport report = buildBaseReport();
        ForensicReportAssembler.Assembly assembled = buildBaseAssembly();

        ForensicConclusionEngine.ForensicConclusion conclusion = ForensicConclusionEngine.build(report, assembled);

        ForensicConclusionEngine.ForensicProposition first = conclusion.forensicPropositions.get(0);
        assertFalse(first.conduct.toLowerCase().contains("fraud"));
        assertEquals("fraud", first.offenceMapping);
    }

    @Test
    public void buildRemainsStableAcrossRerunsForSameInput() throws Exception {
        AnalysisEngine.ForensicReport report = buildBaseReport();
        ForensicReportAssembler.Assembly assembled = buildBaseAssembly();

        String one = ForensicConclusionEngine.build(report, assembled).toJson().toString();
        String two = ForensicConclusionEngine.build(report, assembled).toJson().toString();

        assertEquals(one, two);
    }

    private AnalysisEngine.ForensicReport buildBaseReport() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.caseId = "case-test";
        report.jurisdiction = "ZA";
        report.jurisdictionName = "South Africa";
        report.topLiabilities = new String[] {"Financial Irregularities"};
        report.guardianDecision = new JSONObject().put("approved", true);
        report.diagnostics = new JSONObject()
                .put("processingStatus", "DETERMINATE")
                .put("verifiedContradictionCount", 0)
                .put("candidateContradictionCount", 1)
                .put("contradictionRegister", new JSONArray()
                        .put(new JSONObject().put("neededEvidence", "A fully paired proposition set with direct agreement text would resolve the remaining contradiction.")));
        report.tripleVerification = new JSONObject()
                .put("overall", new JSONObject()
                        .put("status", "PASS")
                        .put("reason", "Material coverage gaps still prevent a mature consensus outcome."))
                .put("antithesis", new JSONObject()
                        .put("verifiedCount", 0)
                        .put("candidateCount", 1));
        return report;
    }

    private ForensicReportAssembler.Assembly buildBaseAssembly() {
        ForensicReportAssembler.Assembly assembled = new ForensicReportAssembler.Assembly();
        assembled.guardianApprovedCertifiedFindingCount = 3;
        assembled.verifiedContradictionCount = 0;
        assembled.candidateContradictionCount = 1;
        assembled.primaryHarmedParty = "Desmond Smith";
        assembled.otherAffectedParties = Collections.singletonList("Greensky Solutions");
        assembled.contradictionPosture = "The engine can state what happened and who is materially implicated, but the contradiction layer has not yet matured into a verified paired conflict.";
        assembled.issueGroups = Arrays.asList(
                new ForensicReportAssembler.IssueCard(
                        "Document execution and pressure pattern",
                        "The sealed record shows a repeated pattern in which papers were advanced, no countersigned copy returned, money continued to move, and pressure to vacate followed.",
                        "It links document execution, reliance, and later pressure events into one anchored pattern.",
                        Collections.singletonList("All Fuels"),
                        Arrays.asList(27, 47, 58),
                        "HIGH",
                        Collections.emptyList()
                ),
                new ForensicReportAssembler.IssueCard(
                        "Document-execution dispute",
                        "Desmond Smith is strongly anchored in the record as the affected party in the document-execution dispute.",
                        "It identifies the directly affected party without changing the contradiction gate.",
                        Collections.singletonList("Desmond Smith"),
                        Arrays.asList(47, 58),
                        "HIGH",
                        Collections.emptyList()
                )
        );
        assembled.chronology = Collections.singletonList(
                new ForensicReportAssembler.ChronologyEvent(
                        "July 2016",
                        "The record anchors the start of the dispute around non-renewal and site pressure.",
                        Arrays.asList("All Fuels", "Desmond Smith"),
                        Arrays.asList(27, 47),
                        "ANCHORED"
                )
        );
        assembled.directOffenceFindings = Collections.singletonList(
                new CanonicalFindingBridge.DirectOffenceFinding(
                        "fraud",
                        "All Fuels",
                        "The record links the actor to the document-execution and reliance pattern.",
                        Arrays.asList(27, 47, 58),
                        "HIGH",
                        "test"
                )
        );
        return assembled;
    }
}
