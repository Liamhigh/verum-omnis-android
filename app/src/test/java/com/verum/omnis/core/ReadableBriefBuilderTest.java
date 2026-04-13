package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

public class ReadableBriefBuilderTest {

    @Test
    public void renderMatchesGreenskyGoldenFixture() throws Exception {
        ReadableBriefModel model = ReadableBriefBuilder.build(buildGreenskyInput());

        String rendered = ReadableBriefBuilder.render(model);
        String expected = loadFixture("tests/fixtures/case-32d3a9e6f5190fa551b2e712/expected-readable-brief.txt");

        assertEquals(normalize(expected), normalize(rendered));
        assertTrue(rendered.contains("Case ID: case-32d3a9e6f5190fa551b2e712"));
        assertTrue(rendered.contains("Evidence SHA-512: 32d3a9e6f5190fa5..."));
        assertTrue(rendered.contains("Guardian-approved certified findings: 3"));
        assertTrue(rendered.contains("Verified contradictions: 0"));
        assertTrue(rendered.contains("Candidate contradiction leads: 1"));
        assertTrue(rendered.contains("(pages 127, 128, 129)"));
        assertFalse(rendered.contains("- Harmed party: Marius Nortje"));
    }

    @Test
    public void renderDoesNotUpgradeCandidateContradictions() {
        ReadableBriefModel.Input input = buildGreenskyInput();
        input.verifiedContradictionCount = 0;
        input.candidateContradictionCount = 1;
        input.contradictionPosture = "Candidate contradiction lead remains candidate-level until a verified contradiction matures.";

        String rendered = ReadableBriefBuilder.render(ReadableBriefBuilder.build(input));

        assertTrue(rendered.contains("Verified contradictions: 0"));
        assertTrue(rendered.contains("Candidate contradiction leads: 1"));
        assertFalse(rendered.contains("Verified contradictions: 1"));
        assertTrue(rendered.contains("candidate-level until a verified contradiction matures"));
    }

    @Test
    public void renderWithholdsHarmedPartyWhenPublicationLayerSaysSuppress() {
        ReadableBriefModel.Input input = buildGreenskyInput();
        input.suppressRoleNarration = true;
        input.primaryHarmedParty = "Marius Nortje";

        String rendered = ReadableBriefBuilder.render(ReadableBriefBuilder.build(input));

        assertTrue(rendered.contains("- Harmed party: this pass does not resolve that role cleanly enough to publish it."));
        assertFalse(rendered.contains("- Harmed party: Marius Nortje"));
    }

    @Test
    public void renderPrefersCanonicalConclusionOverLegacyActorAndOffenceText() {
        ReadableBriefModel.Input input = buildGreenskyInput();
        input.conclusionWhatHappened = "All Fuels is linked in the sealed record to non-renewal, vacate, or eviction-pressure events.";
        input.conclusionPrimaryImplicatedActor = "All Fuels";
        input.conclusionWhy = Arrays.asList(
                "All Fuels is linked in the sealed record to non-renewal, vacate, or eviction-pressure events.",
                "Desmond Smith is linked in the sealed record to primary evidence that a lease, MOU, or renewal was not countersigned or returned."
        );
        input.conclusionTimelineHighlights = Arrays.asList(
                "31 Dec: All Fuels is linked in the sealed record to non-renewal, vacate, or eviction-pressure events. (pages 128, 129, 130, 133).",
                "31 Jul 2016: Desmond Smith is linked in the sealed record to primary evidence that a lease, MOU, or renewal was not countersigned or returned. (pages 28, 48, 40, 62)."
        );
        input.conclusionOtherLinkedActors = Arrays.asList("Desmond Smith", "Gary Highcock");
        input.conclusionBoundary = "This is a forensic conclusion, not a judicial verdict.";
        input.actorConclusion = "The record presently points mainly to Gary Highcock.";
        input.offenceFindings = Collections.singletonList("Gary Highcock is linked in the sealed record to the offence of fraud.");
        input.conclusionPages = Arrays.asList("128", "129", "130", "133");

        String rendered = ReadableBriefBuilder.render(ReadableBriefBuilder.build(input));

        assertTrue(rendered.contains("* What happened: All Fuels is linked in the sealed record to non-renewal, vacate, or eviction-pressure events."));
        assertTrue(rendered.contains("* Primary implicated actor: All Fuels"));
        assertTrue(rendered.contains("- Primary implicated actor: All Fuels"));
        assertTrue(rendered.contains("* Boundary for this pass: This is a forensic conclusion, not a judicial verdict."));
        assertTrue(rendered.contains("* How the record unfolds:"));
        assertTrue(rendered.contains("31 Dec: All Fuels is linked in the sealed record to non-renewal, vacate, or eviction-pressure events."));
        assertTrue(rendered.contains("* Pages to read first: 128, 129, 130, and 133"));
        assertFalse(rendered.contains("Gary Highcock is linked in the sealed record to the offence of fraud."));
        assertFalse(rendered.contains("- Primary adverse actor: The record presently points mainly to Gary Highcock."));
    }

    private ReadableBriefModel.Input buildGreenskyInput() {
        ReadableBriefModel.Input input = new ReadableBriefModel.Input();
        input.caseId = "case-32d3a9e6f5190fa551b2e712";
        input.jurisdictionName = "South Africa";
        input.jurisdictionCode = "ZA";
        input.evidenceHashPrefix = "32d3a9e6f5190fa5...";
        input.guardianApprovedCertifiedFindingCount = 3;
        input.verifiedContradictionCount = 0;
        input.candidateContradictionCount = 1;
        input.truthSummary = "The sealed record centers on a contradiction about whether Kevin's Export proceeded while the agreement remained active.";
        input.patternOriginLine = "certified findings and the contradiction ledger converge on the same order-completion dispute.";
        input.suppressRoleNarration = true;
        input.primaryHarmedParty = "Marius Nortje";
        input.contradictionPosture = "Candidate contradiction lead remains candidate-level until a verified contradiction matures.";
        input.otherAffectedParties = Arrays.asList("Kevin Lappeman", "Greensky Solutions");
        input.visualFindings = Collections.singletonList("A high-severity signature overlay anomaly remains logged for manual verification on page 2.");
        input.certifiedFindings = Arrays.asList(
                new ReadableBriefModel.FindingEntry(
                        "Agreement contradiction",
                        "Marius confirms Kevin completed the order while the agreement remained active",
                        "It anchors the contradiction against later denial that any exclusivity agreement existed.",
                        Arrays.asList(127, 128, 129)),
                new ReadableBriefModel.FindingEntry(
                        "No exclusivity claim",
                        "Kevin denies that any exclusivity agreement existed",
                        "It conflicts with the anchored order-completion position and keeps the contradiction at candidate level until paired review matures.",
                        Collections.singletonList(132)),
                new ReadableBriefModel.FindingEntry(
                        "Eviction pressure position",
                        "The record links the dispute to non-renewal, vacate, or eviction-pressure events",
                        "It shows practical consequences tied to the same dispute.",
                        Arrays.asList(27, 47)));
        input.reviewItems = Collections.singletonList("Review the sealed audit report and findings package for unresolved items and supporting gaps.");
        input.readFirstPages = Arrays.asList("127", "128", "129", "132", "27", "47");
        input.evidencePageHints = Arrays.asList(
                "p. 127 - Agreement contradiction: Marius confirms Kevin completed the order while the agreement remained active (pages 127, 128, 129)",
                "p. 132 - No exclusivity claim: Kevin denies that any exclusivity agreement existed (pages 132)");
        input.immediateNextActions = Arrays.asList(
                "Read the sealed evidence pages listed above before external escalation.",
                "Check the audit report for the full contradiction chain and any failure disclosures.",
                "If a fact is not anchored in the sealed record, treat it as context until supporting evidence is added.");
        input.visualExcerpt = "Visual memo excerpt placeholder.";
        input.auditExcerpt = "Audit excerpt placeholder.";
        return input;
    }

    private String loadFixture(String relativePath) throws IOException {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path[] candidates = new Path[] {
                cwd.resolve(relativePath),
                cwd.getParent() != null ? cwd.getParent().resolve(relativePath) : null
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Fixture not found: " + relativePath);
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").trim();
    }
}
