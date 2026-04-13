package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Builds the governed prompt packet for downstream narrative generation.
 *
 * The packet is intentionally restricted to deterministic engine outputs so
 * the narrative layer cannot read raw evidence or other free-form narrative
 * products and drift away from the constitutional path.
 */
public final class ConstitutionalNarrativePacketBuilder {

    private ConstitutionalNarrativePacketBuilder() {}

    public static String buildForensicReportPacket(
            AnalysisEngine.ForensicReport report,
            String sourceFileName,
            String auditorReport,
            String findingsJson
    ) {
        if (report == null) {
            return "";
        }
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        JSONObject truthFrame = TruthInCodeEngine.buildTruthFrame(report, assembled);
        ForensicConclusionEngine.ForensicConclusion conclusion =
                ForensicConclusionEngine.build(report, assembled);
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, report, sourceFileName);
        appendTruthFrame(sb, truthFrame);
        appendForensicConclusion(sb, conclusion);
        appendAssembly(sb, assembled);
        appendTripleVerification(sb, report != null ? report.tripleVerification : null);
        appendOpenQuestions(sb, truthFrame);
        appendAuditArtifacts(sb, auditorReport, findingsJson, null);
        return sb.toString().trim();
    }

    public static String buildReadableBriefPacket(
            AnalysisEngine.ForensicReport report,
            String sourceFileName,
            String auditReport,
            String findingsJson,
            String visualFindingsMemo
    ) {
        if (report == null) {
            return "";
        }
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        JSONObject truthFrame = TruthInCodeEngine.buildTruthFrame(report, assembled);
        ForensicConclusionEngine.ForensicConclusion conclusion =
                ForensicConclusionEngine.build(report, assembled);
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, report, sourceFileName);
        appendTruthFrame(sb, truthFrame);
        appendForensicConclusion(sb, conclusion);
        appendAssembly(sb, assembled);
        appendTripleVerification(sb, report != null ? report.tripleVerification : null);
        appendOpenQuestions(sb, truthFrame);
        appendAuditArtifacts(sb, auditReport, findingsJson, visualFindingsMemo);
        return sb.toString().trim();
    }

    private static void appendHeader(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            String sourceFileName
    ) {
        sb.append("Constitutional Narrative Packet\n");
        sb.append("Source file: ").append(safe(sourceFileName)).append("\n");
        sb.append("Case ID: ").append(safe(report != null ? report.caseId : null)).append("\n");
        sb.append("Jurisdiction: ").append(safe(report != null ? report.jurisdictionName : null));
        String jurisdictionCode = safe(report != null ? report.jurisdiction : null);
        if (!jurisdictionCode.isEmpty()) {
            sb.append(" (").append(jurisdictionCode).append(")");
        }
        sb.append("\n");
        sb.append("Evidence SHA-512: ").append(safe(report != null ? report.evidenceHashShort : null)).append("\n");
        sb.append("Blockchain anchor: ").append(safe(report != null ? report.blockchainAnchor : null)).append("\n");
        sb.append("Engine summary: ").append(safe(report != null ? report.summary : null)).append("\n\n");
    }

    private static void appendTruthFrame(StringBuilder sb, JSONObject truthFrame) {
        JSONObject safeTruth = truthFrame != null ? truthFrame : new JSONObject();
        sb.append("Truth Frame\n");
        sb.append("What happened: ").append(safe(safeTruth.optString("whatHappened", ""))).append("\n");
        appendArray(sb, "Who did what", safeTruth.optJSONArray("whoDidWhat"), 6);
        appendArray(sb, "Timeline", safeTruth.optJSONArray("when"), 6);
        sb.append("Why it matters: ").append(safe(safeTruth.optString("whyItMatters", ""))).append("\n\n");
    }

    private static void appendForensicConclusion(
            StringBuilder sb,
            ForensicConclusionEngine.ForensicConclusion conclusion
    ) {
        ForensicConclusionEngine.ForensicConclusion safeConclusion =
                conclusion != null ? conclusion : new ForensicConclusionEngine.ForensicConclusion();
        sb.append("Forensic Conclusion\n");
        sb.append("Processing status: ").append(safe(safeConclusion.processingStatus)).append("\n");
        sb.append("Narrative type: ").append(safe(safeConclusion.narrativeType)).append("\n");
        sb.append("Strongest conclusion: ").append(safe(safeConclusion.strongestConclusion)).append("\n");
        sb.append("Publication boundary: ").append(safe(safeConclusion.publicationBoundary)).append("\n");
        appendList(sb, "Certified conduct", safeConclusion.certifiedForensicConduct, 6);
        appendList(sb, "Proof gaps", safeConclusion.proofGaps, 6);
        appendList(sb, "Framework mapping", safeConclusion.frameworkMapping, 6);
        appendForensicPropositions(sb, safeConclusion.forensicPropositions, 6);
        sb.append("\n");
    }

    private static void appendAssembly(
            StringBuilder sb,
            ForensicReportAssembler.Assembly assembled
    ) {
        if (assembled == null) {
            sb.append("Deterministic Engine Output\n");
            sb.append("No deterministic assembly was available in this pass.\n\n");
            return;
        }
        ForensicReportAssembler.Assembly safeAssembly = assembled;
        sb.append("Deterministic Engine Output\n");
        sb.append("Guardian-approved certified findings: ")
                .append(safeAssembly.guardianApprovedCertifiedFindingCount)
                .append("\n");
        sb.append("Verified contradictions: ")
                .append(safeAssembly.verifiedContradictionCount)
                .append("\n");
        sb.append("Candidate contradictions: ")
                .append(safeAssembly.candidateContradictionCount)
                .append("\n");
        appendFindingCards(sb, safeAssembly.certifiedFindings, 6);
        appendIssueCards(sb, safeAssembly.issueGroups, 6);
        appendList(sb, "Read first pages", safeAssembly.readFirstPages, 8);
        sb.append("Contradiction posture: ").append(safe(safeAssembly.contradictionPosture)).append("\n\n");
    }

    private static void appendTripleVerification(StringBuilder sb, JSONObject tripleVerification) {
        JSONObject safeTriple = tripleVerification != null ? tripleVerification : new JSONObject();
        sb.append("Triple Verification\n");
        sb.append("Thesis: ").append(describeTripleVerificationBranch(safeTriple.optJSONObject("thesis"))).append("\n");
        sb.append("Antithesis: ").append(describeTripleVerificationBranch(safeTriple.optJSONObject("antithesis"))).append("\n");
        sb.append("Synthesis: ").append(describeTripleVerificationBranch(safeTriple.optJSONObject("synthesis"))).append("\n\n");
    }

    private static void appendOpenQuestions(StringBuilder sb, JSONObject truthFrame) {
        JSONObject safeTruth = truthFrame != null ? truthFrame : new JSONObject();
        appendArray(sb, "Open questions", safeTruth.optJSONArray("openQuestions"), 6);
        sb.append("\n");
    }

    private static void appendAuditArtifacts(
            StringBuilder sb,
            String auditReport,
            String findingsJson,
            String visualFindingsMemo
    ) {
        sb.append("Audit Record Excerpts\n");
        sb.append("Auditor forensic report:\n").append(clip(auditReport, 12000)).append("\n");
        if (!safe(findingsJson).isEmpty()) {
            sb.append("\nFindings package excerpt:\n").append(clip(findingsJson, 2500)).append("\n");
        }
        if (!safe(visualFindingsMemo).isEmpty()) {
            sb.append("\nVisual findings memo excerpt:\n").append(clip(visualFindingsMemo, 3500)).append("\n");
        }
    }

    private static void appendForensicPropositions(
            StringBuilder sb,
            List<ForensicConclusionEngine.ForensicProposition> propositions,
            int limit
    ) {
        sb.append("Forensic propositions:\n");
        if (propositions == null || propositions.isEmpty()) {
            sb.append("- None published in this pass.\n");
            return;
        }
        int count = 0;
        for (ForensicConclusionEngine.ForensicProposition proposition : propositions) {
            if (proposition == null) {
                continue;
            }
            sb.append("- ")
                    .append(safe(proposition.actor))
                    .append(" | ")
                    .append(safe(proposition.conduct))
                    .append(" | pages ")
                    .append(proposition.anchorPages)
                    .append(" | status ")
                    .append(safe(proposition.status))
                    .append("\n");
            count++;
            if (count >= Math.max(1, limit)) {
                break;
            }
        }
    }

    private static void appendFindingCards(
            StringBuilder sb,
            List<ForensicReportAssembler.FindingCard> findings,
            int limit
    ) {
        sb.append("Certified findings:\n");
        if (findings == null || findings.isEmpty()) {
            sb.append("- None published in this pass.\n");
            return;
        }
        int count = 0;
        for (ForensicReportAssembler.FindingCard finding : findings) {
            if (finding == null) {
                continue;
            }
            sb.append("- ").append(safe(finding.toTechnicalLine())).append("\n");
            count++;
            if (count >= Math.max(1, limit)) {
                break;
            }
        }
    }

    private static void appendIssueCards(
            StringBuilder sb,
            List<ForensicReportAssembler.IssueCard> issues,
            int limit
    ) {
        sb.append("Issue ledger:\n");
        if (issues == null || issues.isEmpty()) {
            sb.append("- No contradiction-led issue group matured in this pass.\n");
            return;
        }
        int count = 0;
        for (ForensicReportAssembler.IssueCard issue : issues) {
            if (issue == null) {
                continue;
            }
            sb.append("- ")
                    .append(safe(issue.title))
                    .append(": ")
                    .append(safe(issue.summary))
                    .append(" | pages ")
                    .append(issue.evidencePages)
                    .append(" | actors ")
                    .append(issue.actors)
                    .append("\n");
            count++;
            if (count >= Math.max(1, limit)) {
                break;
            }
        }
    }

    private static void appendArray(StringBuilder sb, String heading, JSONArray array, int limit) {
        sb.append(heading).append(":\n");
        if (array == null || array.length() == 0) {
            sb.append("- None published in this pass.\n");
            return;
        }
        for (int i = 0; i < array.length() && i < Math.max(1, limit); i++) {
            String value = safe(array.optString(i, ""));
            if (!value.isEmpty()) {
                sb.append("- ").append(value).append("\n");
            }
        }
    }

    private static void appendList(StringBuilder sb, String heading, List<?> values, int limit) {
        sb.append(heading).append(":\n");
        if (values == null || values.isEmpty()) {
            sb.append("- None published in this pass.\n");
            return;
        }
        int count = 0;
        for (Object value : values) {
            String cleaned = safe(value != null ? String.valueOf(value) : "");
            if (cleaned.isEmpty()) {
                continue;
            }
            sb.append("- ").append(cleaned).append("\n");
            count++;
            if (count >= Math.max(1, limit)) {
                break;
            }
        }
    }

    private static String describeTripleVerificationBranch(JSONObject branch) {
        if (branch == null) {
            return "No branch data published in this pass.";
        }
        String summary = safe(branch.optString("summary", ""));
        if (!summary.isEmpty()) {
            return summary;
        }
        return "verified="
                + branch.optInt("verifiedCount", 0)
                + ", candidate="
                + branch.optInt("candidateCount", 0)
                + ", certified="
                + branch.optInt("certifiedFindingCount", 0);
    }

    private static String clip(String value, int maxChars) {
        String cleaned = safe(value);
        if (cleaned.length() <= Math.max(1, maxChars)) {
            return cleaned;
        }
        return cleaned.substring(0, Math.max(1, maxChars)).trim() + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
