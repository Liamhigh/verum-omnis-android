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

public class ContradictionReportBuilderTest {

    @Test
    public void renderMatchesGreenskyGoldenFixture() throws Exception {
        ContradictionReportModel model = ContradictionReportBuilder.build(buildGreenskyInput());

        String rendered = ContradictionReportBuilder.render(model);
        String expected = loadFixture("tests/fixtures/case-32d3a9e6f5190fa551b2e712/expected-contradiction-report.txt");

        assertEquals(normalize(expected), normalize(rendered));
        assertTrue(rendered.contains("Verified contradictions: 0"));
        assertTrue(rendered.contains("Candidate contradictions: 1"));
        assertTrue(rendered.contains("Processing status: DETERMINATE WITH MATERIAL COVERAGE GAPS"));
        assertTrue(rendered.contains("It does not assign harmed-party labels unless those labels are separately anchored elsewhere in the sealed record."));
        assertFalse(rendered.contains("Harmed party: Marius Nortje"));
    }

    @Test
    public void renderKeepsContradictionStatusNarrow() {
        ContradictionReportModel.Input input = buildGreenskyInput();
        input.executiveSummary = "A candidate contradiction remains open and requires paired-proposition review.";

        String rendered = ContradictionReportBuilder.render(ContradictionReportBuilder.build(input));

        assertTrue(rendered.contains("Verified contradictions: 0"));
        assertTrue(rendered.contains("Candidate contradictions: 1"));
        assertTrue(rendered.contains("candidate contradiction remains open"));
        assertFalse(rendered.contains("Verified contradictions: 1"));
    }

    @Test
    public void renderDoesNotInjectLegalOrHarmedPartyNarrationIntoLimitations() {
        String rendered = ContradictionReportBuilder.render(ContradictionReportBuilder.build(buildGreenskyInput()));

        assertTrue(rendered.contains("It is not a substitute for the sealed evidence pages"));
        assertTrue(rendered.contains("should not be expanded into harmed-party or liability conclusions unless those are separately anchored"));
        assertFalse(rendered.contains("Direct offence finding"));
    }

    private ContradictionReportModel.Input buildGreenskyInput() {
        ContradictionReportModel.Input input = new ContradictionReportModel.Input();
        input.constitutionalVersion = "v5.2.7";
        input.reportType = "Direct contradiction-engine output";
        input.inputArtifact = "greenskycomplete_compressed_compressed1.zip";
        input.reportDateUtc = "2026-04-07";
        input.verifiedContradictions = 0;
        input.candidateContradictions = 1;
        input.processingStatus = "DETERMINATE WITH MATERIAL COVERAGE GAPS";
        input.ordinalConfidence = "MODERATE";
        input.executiveSummary = "The anchored contradiction ledger centers on whether Kevin's Export proceeded while an active agreement existed, but the record still discloses material coverage gaps.";
        input.contradictionPosture = "Candidate contradiction lead remains candidate-level until a verified contradiction matures.";
        input.truthSection = "The direct contradiction engine ties the present dispute to anchored claims about order completion, agreement status, and later denial.\nWHO APPEARS LINKED TO THE KEY EVENTS\nKevin Lappeman and Marius Nortje appear in the anchored contradiction set.\nWHEN THE KEY EVENTS APPEAR TO HAVE HAPPENED\n6 April 2025 appears in the anchored timeline.\nWHY THIS MATTERS\nThe contradiction remains publishable, but it is not yet mature enough to justify expanded role narration.";
        input.evidenceManifest = "- Sealed evidence hash prefix: 32d3a9e6f5190fa5...\n- Read-first pages: 127, 128, 129, 132, 27, 47";
        input.chainOfCustody = "- Sealed bundle archived with deterministic case ID case-32d3a9e6f5190fa551b2e712.";
        input.contradictionLedger = "- Candidate contradiction: \"Kevin's Export proceeded with the deal\" versus \"No exclusivity agreement existed\" (pages 127, 132).";
        input.anchoredTimeline = "- 6 April 2025: Marius Nortje email quoted in the contradiction set (page 86).";
        input.nineBrainOutputs = "- B1 contradiction pressure recorded.\n- B7 legal mapping withheld from this artifact.";
        input.tripleVerification = "- Overall status remains determinate with material coverage gaps.\n- Verified contradictions: 0.\n- Candidate contradictions: 1.";
        input.resolutionGuidance = "- A fully paired proposition set with direct agreement text would resolve the remaining candidate contradiction.";
        input.coverageGaps = "- Material coverage gaps remain disclosed in the sealed record.\n- Concealment handling remains active where the contradiction has not matured.";
        input.sealBlock = "- Case ID: case-32d3a9e6f5190fa551b2e712\n- SHA-512: 32d3a9e6f5190fa5...";
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
