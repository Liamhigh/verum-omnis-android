package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ForensicReportAssemblerTest {

    @Test
    public void assembleBuildsPlainLanguageCasePictureFromCertifiedFindings() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.guardianDecision = new JSONObject()
                .put("approved", true)
                .put("approvedCount", 2);
        report.tripleVerification = new JSONObject()
                .put("antithesis", new JSONObject()
                        .put("verifiedCount", 0)
                        .put("candidateCount", 1))
                .put("synthesis", new JSONObject()
                        .put("certifiedFindingCount", 2));
        report.forensicSynthesis = new JSONObject()
                .put("victimActors", new JSONArray().put("Marius Nortje"))
                .put("wrongfulActorProfile", new JSONObject()
                        .put("actor", "Kevin Lappeman")
                        .put("verifiedContradictionCount", 0));
        report.certifiedFindings = new JSONArray()
                .put(certifiedFinding(
                        "CANDIDATE",
                        "INTER_ACTOR_CONFLICT",
                        "Kevin Lappeman",
                        "Quoted admissions and denials conflict on whether Kevin's Export proceeded with the deal.",
                        86))
                .put(certifiedFinding(
                        "CANDIDATE",
                        "FINANCIAL_RECONCILIATION",
                        "Liam Highcock",
                        "The sealed record links the profit-share dispute to anchored payment references.",
                        54));

        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);

        assertEquals(2, assembled.guardianApprovedCertifiedFindingCount);
        assertEquals(0, assembled.verifiedContradictionCount);
        assertEquals("Marius Nortje", assembled.primaryHarmedParty);
        assertTrue(assembled.actorConclusion.contains("Kevin Lappeman"));
        assertTrue(assembled.actorConclusion.contains("working conclusion"));
        assertEquals(2, assembled.certifiedFindings.size());
        assertEquals(2, assembled.issueGroups.size());
        assertFalse(assembled.readFirstPages.isEmpty());
        assertTrue(assembled.contradictionPosture.contains("contradiction pressure"));
    }

    @Test
    public void assembleFiltersPublicationNoiseFromAffectedParties() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.guardianDecision = new JSONObject().put("approved", true).put("approvedCount", 1);
        report.tripleVerification = new JSONObject()
                .put("antithesis", new JSONObject().put("verifiedCount", 0).put("candidateCount", 0))
                .put("synthesis", new JSONObject().put("certifiedFindingCount", 1));
        report.forensicSynthesis = new JSONObject()
                .put("victimActors", new JSONArray()
                        .put("Liam Highcock")
                        .put("FCDO Correspondence")
                        .put("Development Office")
                        .put("Verifiable Global Exposure"));
        report.certifiedFindings = new JSONArray()
                .put(certifiedFinding(
                        "CANDIDATE",
                        "FINANCIAL_RECONCILIATION",
                        "Liam Highcock",
                        "The sealed record links the profit-share dispute to anchored payment references.",
                        54));

        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);

        assertEquals("Liam Highcock", assembled.primaryHarmedParty);
        assertTrue(assembled.otherAffectedParties.isEmpty());
    }

    @Test
    public void assembleCollapsesDuplicateContradictionFamilyIntoOneIssueCard() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.guardianDecision = new JSONObject().put("approved", true).put("approvedCount", 2);
        report.tripleVerification = new JSONObject()
                .put("antithesis", new JSONObject().put("verifiedCount", 2).put("candidateCount", 0))
                .put("synthesis", new JSONObject().put("certifiedFindingCount", 2));
        report.forensicSynthesis = new JSONObject()
                .put("victimActors", new JSONArray().put("Marius Nortje"))
                .put("wrongfulActorProfile", new JSONObject()
                        .put("actor", "Kevin Lappeman")
                        .put("verifiedContradictionCount", 2));
        report.certifiedFindings = new JSONArray()
                .put(certifiedFinding(
                        "VERIFIED",
                        "INTER_ACTOR_CONFLICT",
                        "Kevin Lappeman",
                        "The record contains a verified contradiction about the deal: \"Kevin's Export proceeded with the deal\" and \"No exclusivity agreement ever existed\" cannot both stand.",
                        26))
                .put(certifiedFinding(
                        "VERIFIED",
                        "INTER_ACTOR_CONFLICT",
                        "Kevin Lappeman",
                        "The record contains a verified contradiction about the deal: \"Kevin's Export proceeded with the deal\" and \"No exclusivity agreement ever existed\" cannot both stand.",
                        95));

        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);

        assertEquals(2, assembled.certifiedFindings.size());
        assertEquals(1, assembled.issueGroups.size());
        assertTrue(assembled.issueGroups.get(0).summary.contains("brings that contradiction together in one place"));
    }

    @Test
    public void assembleUsesCertifiedVictimSupportAndActorFallbackToFilterTdpiStyleNoise() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.guardianDecision = new JSONObject().put("approved", true).put("approvedCount", 3);
        report.tripleVerification = new JSONObject()
                .put("antithesis", new JSONObject().put("verifiedCount", 0).put("candidateCount", 1))
                .put("synthesis", new JSONObject().put("certifiedFindingCount", 3));
        report.forensicSynthesis = new JSONObject()
                .put("victimActors", new JSONArray()
                        .put("Liam Highcock")
                        .put("Desmond Smith")
                        .put("Wayne Nel")
                        .put("Nealy Lombaard")
                        .put("Gary Highcock")
                        .put("Liam Highcock Founder")
                        .put("Silicia Scheppel")
                        .put("Dez")
                        .put("Modus Operandi"))
                .put("wrongfulActorProfile", new JSONObject()
                        .put("actor", "Tel")
                        .put("verifiedContradictionCount", 0));
        report.constitutionalExtraction = new JSONObject()
                .put("namedParties", new JSONArray()
                        .put(namedParty("Liam Highcock", "VICTIM", 36, 33, 0, 3))
                        .put(namedParty("All Fuels", "ACTOR", 53, 0, 1, 52))
                        .put(namedParty("Desmond Smith", "VICTIM", 17, 0, 5, 12))
                        .put(namedParty("Gary Highcock", "VICTIM", 10, 1, 2, 7))
                        .put(namedParty("Wayne Nel", "VICTIM", 8, 0, 1, 7))
                        .put(namedParty("Nealy Lombaard", "VICTIM", 11, 8, 1, 2))
                        .put(namedParty("Liam Highcock Founder", "VICTIM", 6, 0, 2, 4))
                        .put(namedParty("Silicia Scheppel", "VICTIM", 5, 4, 0, 1))
                        .put(namedParty("Modus Operandi", "VICTIM", 4, 0, 0, 4)));
        report.certifiedFindings = new JSONArray()
                .put(certifiedFinding(
                        "CANDIDATE",
                        "OVERSIGHT_PATTERN",
                        "All Fuels",
                        "On 31 Dec, All Fuels is linked to non-renewal, vacate, or eviction-pressure events.",
                        127))
                .put(certifiedFinding(
                        "CANDIDATE",
                        "OVERSIGHT_PATTERN",
                        "Desmond Smith",
                        "Desmond Smith is linked to primary evidence that a lease, MOU, or renewal was not countersigned or returned.",
                        27))
                .put(certifiedFinding(
                        "CANDIDATE",
                        "OVERSIGHT_PATTERN",
                        "Des",
                        "Des is linked to primary evidence that a lease, MOU, or renewal was not countersigned or returned.",
                        50));

        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);

        assertEquals("Desmond Smith", assembled.primaryHarmedParty);
        assertTrue(assembled.actorConclusion.contains("All Fuels"));
        assertFalse(assembled.otherAffectedParties.contains("Liam Highcock"));
        assertFalse(assembled.otherAffectedParties.contains("Silicia Scheppel"));
        assertFalse(assembled.otherAffectedParties.contains("Modus Operandi"));
        assertTrue(assembled.otherAffectedParties.contains("Gary Highcock"));
        assertTrue(assembled.otherAffectedParties.contains("Wayne Nel"));
        assertEquals(2, assembled.issueGroups.size());
    }

    @Test
    public void assembleLeavesHarmedPartyBlankWhenCaseIsContradictionLedOnly() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.guardianDecision = new JSONObject().put("approved", true).put("approvedCount", 2);
        report.tripleVerification = new JSONObject()
                .put("antithesis", new JSONObject().put("verifiedCount", 8).put("candidateCount", 7))
                .put("synthesis", new JSONObject().put("certifiedFindingCount", 2));
        report.forensicSynthesis = new JSONObject();
        report.certifiedFindings = new JSONArray()
                .put(certifiedFinding(
                        "VERIFIED",
                        "INTER_ACTOR_CONFLICT",
                        "Kevin Lappeman",
                        "The record contains a verified contradiction about the agreement: \"Kevin's Export proceeded with the deal\" and \"No exclusivity agreement ever existed\" cannot both stand.",
                        89))
                .put(certifiedFinding(
                        "VERIFIED",
                        "INTER_ACTOR_CONFLICT",
                        "Marius Nortje",
                        "The record contains a verified contradiction about the agreement: \"Kevin's Export proceeded with the deal\" and \"No exclusivity agreement ever existed\" cannot both stand.",
                        92));

        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);

        assertEquals("", assembled.primaryHarmedParty);
        assertTrue(assembled.actorConclusion.contains("Kevin Lappeman")
                || assembled.actorConclusion.contains("Marius Nortje")
                || assembled.actorConclusion.contains("main adverse actor"));
        assertTrue(assembled.otherAffectedParties.isEmpty());
    }

    @Test
    public void truthInCodeEngineRendersWhatWhoWhenWhyFromAnchoredAssembly() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.guardianDecision = new JSONObject().put("approved", true).put("approvedCount", 2);
        report.tripleVerification = new JSONObject()
                .put("antithesis", new JSONObject().put("verifiedCount", 1).put("candidateCount", 1))
                .put("synthesis", new JSONObject().put("certifiedFindingCount", 2));
        report.forensicSynthesis = new JSONObject()
                .put("victimActors", new JSONArray().put("Marius Nortje"))
                .put("wrongfulActorProfile", new JSONObject()
                        .put("actor", "Kevin Lappeman")
                        .put("verifiedContradictionCount", 1));
        report.constitutionalExtraction = new JSONObject()
                .put("timelineAnchorRegister", new JSONArray()
                        .put(new JSONObject()
                                .put("date", "6 April 2025")
                                .put("summary", "Quoted from Marius Nortje's email dated 6 April 2025.")
                                .put("actor", "Marius Nortje")
                                .put("page", 86)
                                .put("anchors", new JSONArray().put(new JSONObject().put("page", 86)))));
        report.certifiedFindings = new JSONArray()
                .put(certifiedFinding(
                        "VERIFIED",
                        "INTER_ACTOR_CONFLICT",
                        "Kevin Lappeman",
                        "The record contains a verified contradiction about the deal: \"Kevin's Export proceeded with the deal\" and \"No exclusivity agreement ever existed\" cannot both stand.",
                        89))
                .put(certifiedFinding(
                        "CANDIDATE",
                        "FINANCIAL_RECONCILIATION",
                        "Marius Nortje",
                        "The sealed record links the profit-share dispute to anchored payment references.",
                        54));

        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        String truthSection = TruthInCodeEngine.renderTruthSection(report, assembled);

        assertTrue(truthSection.contains("WHAT THE RECORD CURRENTLY SHOWS"));
        assertTrue(truthSection.contains("WHO APPEARS LINKED TO THE KEY EVENTS"));
        assertTrue(truthSection.contains("WHEN THE KEY EVENTS APPEAR TO HAVE HAPPENED"));
        assertTrue(truthSection.contains("WHY THIS MATTERS"));
        assertTrue(truthSection.contains("Kevin Lappeman"));
        assertTrue(truthSection.contains("Marius Nortje"));
        assertTrue(truthSection.contains("6 April 2025"));
    }

    private JSONObject certifiedFinding(
            String status,
            String type,
            String actor,
            String summary,
            int page
    ) throws Exception {
        JSONObject finding = new JSONObject()
                .put("status", status)
                .put("actor", actor)
                .put("findingType", type)
                .put("summary", summary)
                .put("page", page)
                .put("anchors", new JSONArray().put(new JSONObject().put("page", page)));
        return new JSONObject()
                .put("status", status)
                .put("summary", summary)
                .put("page", page)
                .put("actor", actor)
                .put("type", type)
                .put("certification", new JSONObject()
                        .put("guardianApproval", true))
                .put("finding", finding)
                .put("anchors", new JSONArray().put(new JSONObject().put("page", page)));
    }

    private JSONObject namedParty(
            String name,
            String role,
            int occurrences,
            int headerEvidenceCount,
            int victimCueCount,
            int offenceCueCount
    ) throws Exception {
        return new JSONObject()
                .put("name", name)
                .put("role", role)
                .put("actorClass", role)
                .put("occurrences", occurrences)
                .put("headerEvidenceCount", headerEvidenceCount)
                .put("victimCueCount", victimCueCount)
                .put("offenceCueCount", offenceCueCount);
    }
}
