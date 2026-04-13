package com.verum.omnis.core;

public final class ContradictionReportBuilder {

    private ContradictionReportBuilder() {}

    public static ContradictionReportModel build(ContradictionReportModel.Input input) {
        return new ContradictionReportModel(input == null ? new ContradictionReportModel.Input() : input);
    }

    public static String render(ContradictionReportModel model) {
        if (model == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("VERUM OMNIS CONTRADICTION ENGINE REPORT\n");
        sb.append("Constitutional Version: ").append(model.constitutionalVersion).append("\n");
        sb.append("Report Type: ").append(model.reportType).append("\n");
        sb.append("Input Artifact: ").append(model.inputArtifact).append("\n");
        sb.append("Report Date (UTC): ").append(model.reportDateUtc).append("\n");
        sb.append("Engine Mode: ").append(model.engineMode).append("\n");
        sb.append("---\n");
        sb.append("ENGINE SUMMARY\n");
        sb.append("Verified contradictions: ").append(model.verifiedContradictions).append("\n");
        sb.append("Candidate contradictions: ").append(model.candidateContradictions).append("\n");
        sb.append("Processing status: ").append(model.processingStatus).append("\n");
        sb.append("Ordinal confidence: ").append(model.ordinalConfidence).append("\n");
        sb.append("Executive summary: ").append(model.executiveSummary).append("\n");
        sb.append("Contradiction posture: ").append(model.contradictionPosture).append("\n");
        sb.append("Note: this artifact publishes the contradiction engine directly. ");
        sb.append("It does not assign harmed-party labels unless those labels are separately anchored elsewhere in the sealed record.\n");
        sb.append("---\n");
        appendSection(sb, "1. WHAT THE RECORD CURRENTLY SHOWS", model.truthSection);
        appendSection(sb, "2. EVIDENCE MANIFEST", model.evidenceManifest);
        appendSection(sb, "3. CHAIN-OF-CUSTODY LOG", model.chainOfCustody);
        appendSection(sb, "4. CONTRADICTION LEDGER", model.contradictionLedger);
        appendSection(sb, "5. ANCHORED TIMELINE", model.anchoredTimeline);
        appendSection(sb, "6. NINE-BRAIN OUTPUTS (Anchored, Ordinal Confidence Only)", model.nineBrainOutputs);
        appendSection(sb, "7. TRIPLE VERIFICATION", model.tripleVerification);
        appendSection(sb, "8. WHAT WOULD RESOLVE THE OPEN CONTRADICTIONS", model.resolutionGuidance);
        appendSection(sb, "9. DISCLOSED GAPS AND CONCEALMENT HANDLING", model.coverageGaps);
        appendSection(sb, "10. SEAL BLOCK", model.sealBlock);
        sb.append("11. DISCLOSURE OF LIMITATIONS\n");
        if (!model.limitations.isEmpty()) {
            sb.append(model.limitations).append("\n");
        }
        sb.append("This artifact is the contradiction engine speaking in its own voice. ");
        sb.append("It is not a substitute for the sealed evidence pages, and it should not be expanded into harmed-party or liability conclusions unless those are separately anchored.\n");
        sb.append("Visual signature, forgery, and document-image findings remain a separate specialist memo and must be read alongside this report where those issues matter.\n");
        return sb.toString().trim();
    }

    private static void appendSection(StringBuilder sb, String title, String body) {
        sb.append(title).append("\n");
        if (!safe(body).isEmpty()) {
            sb.append(body).append("\n");
        }
        sb.append("---\n");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
