package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Typed bridge between the mixed engine outputs and the publication layer.
 *
 * This does not replace the deeper EvidenceRecord -> Atom -> TypedClaim pipeline.
 * It gives the report family one canonical set of typed findings so the wording
 * layer stops inventing structure differently in each report.
 */
public final class CanonicalFindingBridge {

    public static final class DirectOffenceFinding {
        public final String offence;
        public final String actor;
        public final String summary;
        public final List<Integer> evidencePages;
        public final String confidence;
        public final String sourceRegister;

        DirectOffenceFinding(
                String offence,
                String actor,
                String summary,
                List<Integer> evidencePages,
                String confidence,
                String sourceRegister
        ) {
            this.offence = offence;
            this.actor = actor;
            this.summary = summary;
            this.evidencePages = evidencePages;
            this.confidence = confidence;
            this.sourceRegister = sourceRegister;
        }

        public String toReportLine() {
            StringBuilder sb = new StringBuilder();
            if (!actor.isEmpty()) {
                sb.append(actor).append(" is linked in the sealed record to the offence of ").append(offence);
            } else {
                sb.append("The sealed record supports the offence of ").append(offence);
            }
            if (!summary.isEmpty()) {
                sb.append(": ").append(summary);
            }
            if (!evidencePages.isEmpty()) {
                sb.append(" (pages ").append(joinPages(evidencePages)).append(")");
            }
            return ensureSentence(sb.toString());
        }
    }

    public static final class BehaviouralAggravationFinding {
        public final String category;
        public final String summary;
        public final String confidence;
        public final String sourceRegister;

        BehaviouralAggravationFinding(
                String category,
                String summary,
                String confidence,
                String sourceRegister
        ) {
            this.category = category;
            this.summary = summary;
            this.confidence = confidence;
            this.sourceRegister = sourceRegister;
        }

        public String toReportLine() {
            return ensureSentence("Behavioural aggravation finding: " + summary);
        }
    }

    public static final class VisualForgeryFinding {
        public final String classification;
        public final int page;
        public final String summary;
        public final String severity;
        public final String sourceRegister;

        VisualForgeryFinding(
                String classification,
                int page,
                String summary,
                String severity,
                String sourceRegister
        ) {
            this.classification = classification;
            this.page = page;
            this.summary = summary;
            this.severity = severity;
            this.sourceRegister = sourceRegister;
        }

        public String toReportLine() {
            return ensureSentence(summary);
        }
    }

    public static final class FindingsBundle {
        public final List<DirectOffenceFinding> offenceFindings = new ArrayList<>();
        public final List<BehaviouralAggravationFinding> behaviouralFindings = new ArrayList<>();
        public final List<VisualForgeryFinding> visualFindings = new ArrayList<>();
    }

    private CanonicalFindingBridge() {}

    public static FindingsBundle build(
            AnalysisEngine.ForensicReport report,
            List<ForensicReportAssembler.IssueCard> issueGroups,
            String leadActor
    ) {
        FindingsBundle out = new FindingsBundle();
        buildOffenceFindings(out, report, issueGroups, trimToEmpty(leadActor));
        buildBehaviouralFindings(out, report);
        buildVisualFindings(out, report);
        return out;
    }

    private static void buildOffenceFindings(
            FindingsBundle out,
            AnalysisEngine.ForensicReport report,
            List<ForensicReportAssembler.IssueCard> issueGroups,
            String leadActor
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (report.topLiabilities != null) {
            for (String item : report.topLiabilities) {
                String offence = normalizeOffenceLabel(item);
                if (offence.isEmpty()) {
                    continue;
                }
                ForensicReportAssembler.IssueCard support = findSupportingIssueForOffence(offence, issueGroups);
                List<Integer> pages = support != null ? support.evidencePages : new ArrayList<Integer>();
                String summary = support != null ? trimToEmpty(support.summary) : "";
                String key = lowerUs(offence) + "|" + lowerUs(leadActor) + "|" + lowerUs(summary) + "|" + joinPages(pages);
                if (!seen.add(key)) {
                    continue;
                }
                out.offenceFindings.add(new DirectOffenceFinding(
                        offence,
                        leadActor,
                        summary,
                        new ArrayList<>(pages),
                        support != null ? trimToEmpty(support.confidence) : "HIGH",
                        "topLiabilities/issueGroups"
                ));
                if (out.offenceFindings.size() >= 6) {
                    return;
                }
            }
        }

        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
        JSONArray subjects = extraction.optJSONArray("criticalLegalSubjects");
        if (subjects == null) {
            return;
        }
        for (int i = 0; i < subjects.length() && out.offenceFindings.size() < 8; i++) {
            JSONObject subject = subjects.optJSONObject(i);
            if (subject == null) {
                continue;
            }
            String offence = normalizeOffenceLabel(subject.optString("subject", ""));
            if (offence.isEmpty()) {
                continue;
            }
            String summary = cleanNarrative(firstNonEmpty(
                    subject.optString("summary", null),
                    subject.optString("excerpt", null),
                    subject.optString("description", null)
            ));
            int page = subject.optInt("page", 0);
            List<Integer> pages = new ArrayList<>();
            if (page > 0) {
                pages.add(page);
            }
            String key = lowerUs(offence) + "|" + lowerUs(leadActor) + "|" + lowerUs(summary) + "|" + joinPages(pages);
            if (!seen.add(key)) {
                continue;
            }
            out.offenceFindings.add(new DirectOffenceFinding(
                    offence,
                    leadActor,
                    summary,
                    pages,
                    trimToEmpty(subject.optString("confidence", "HIGH")),
                    "criticalLegalSubjects"
            ));
        }
    }

    private static void buildBehaviouralFindings(FindingsBundle out, AnalysisEngine.ForensicReport report) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        collectBehaviouralMatches(out.behaviouralFindings, seen,
                report.patternAnalysis != null ? report.patternAnalysis.optJSONArray("matches") : null,
                "patternAnalysis");
        collectBehaviouralMatches(out.behaviouralFindings, seen,
                report.vulnerabilityAnalysis != null ? report.vulnerabilityAnalysis.optJSONArray("matches") : null,
                "vulnerabilityAnalysis");
        JSONObject vulnerability = report.vulnerabilityAnalysis != null ? report.vulnerabilityAnalysis : new JSONObject();
        JSONArray indicators = vulnerability.optJSONArray("indicators");
        if (indicators == null) {
            return;
        }
        for (int i = 0; i < indicators.length() && out.behaviouralFindings.size() < 6; i++) {
            String indicator = trimToEmpty(indicators.optString(i, ""));
            if (indicator.isEmpty() || !seen.add(lowerUs(indicator))) {
                continue;
            }
            out.behaviouralFindings.add(new BehaviouralAggravationFinding(
                    "behavioural aggravation",
                    indicator,
                    trimToEmpty(vulnerability.optString("assessment", "possible")),
                    "vulnerabilityAnalysis.indicators"
            ));
        }
    }

    private static void collectBehaviouralMatches(
            List<BehaviouralAggravationFinding> sink,
            LinkedHashSet<String> seen,
            JSONArray matches,
            String sourceRegister
    ) {
        if (matches == null) {
            return;
        }
        for (int i = 0; i < matches.length() && sink.size() < 6; i++) {
            JSONObject match = matches.optJSONObject(i);
            if (match == null) {
                continue;
            }
            String line = firstNonEmpty(
                    match.optString("reportLanguage", null),
                    match.optString("evidenceNote", null)
            );
            if (line.isEmpty() || !seen.add(lowerUs(line))) {
                continue;
            }
            sink.add(new BehaviouralAggravationFinding(
                    trimToEmpty(match.optString("category", "behavioural aggravation")),
                    line,
                    trimToEmpty(match.optString("status", "possible")),
                    sourceRegister
            ));
        }
    }

    private static void buildVisualFindings(FindingsBundle out, AnalysisEngine.ForensicReport report) {
        JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();
        JSONArray visualFindings = nativeEvidence.optJSONArray("visualFindings");
        if (visualFindings == null) {
            return;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < visualFindings.length() && out.visualFindings.size() < 6; i++) {
            JSONObject item = visualFindings.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = trimToEmpty(item.optString("type", ""));
            String severity = lowerUs(item.optString("severity", ""));
            int page = item.optInt("page", 0);
            String summary = "";
            String classification = "";
            if ("SIGNATURE_ZONE_OVERLAY_SUSPECTED".equals(type) && ("high".equals(severity) || "critical".equals(severity))) {
                classification = "forgery";
                summary = "Forgery finding: page " + page + " contains a signature-zone overlay anomaly consistent with a pasted, masked, or whiteout signature panel";
            } else if ("SIGNATURE_REGION_EMPTY".equals(type) && ("medium".equals(severity) || "high".equals(severity))) {
                classification = "execution failure";
                summary = "Execution finding: page " + page + " carries a materially blank signature region where a signature block would be expected";
            } else if ("SIGNATURE_MARKS_NOT_FOUND".equals(type) && ("medium".equals(severity) || "high".equals(severity))) {
                classification = "execution failure";
                summary = "Execution finding: page " + page + " shows no strong signature stroke pattern in the expected signing zone";
            }
            String key = lowerUs(classification) + "|" + page + "|" + lowerUs(summary);
            if (classification.isEmpty() || summary.isEmpty() || !seen.add(key)) {
                continue;
            }
            out.visualFindings.add(new VisualForgeryFinding(
                    classification,
                    page,
                    summary,
                    severity,
                    "nativeEvidence.visualFindings"
            ));
        }
    }

    private static ForensicReportAssembler.IssueCard findSupportingIssueForOffence(
            String offence,
            List<ForensicReportAssembler.IssueCard> issueGroups
    ) {
        if (issueGroups == null || issueGroups.isEmpty()) {
            return null;
        }
        String lower = lowerUs(offence);
        for (ForensicReportAssembler.IssueCard issue : issueGroups) {
            String corpus = lowerUs(issue.title + " " + issue.summary + " " + issue.whyItMatters);
            if ("fraud".equals(lower) && containsAny(corpus, "fraud", "misrepresentation", "false", "contradiction", "cannot both stand")) {
                return issue;
            }
            if (containsAny(lower, "cybercrime", "unauthorized access") && containsAny(corpus, "google archive", "archive request", "unauthorized", "cyber")) {
                return issue;
            }
            if (containsAny(lower, "fiduciary", "shareholder oppression", "unlawful exclusion") && containsAny(corpus, "shareholder", "proceeded with the deal", "private meeting", "excluded")) {
                return issue;
            }
            if (containsAny(lower, "tampering", "spoliation", "forgery") && containsAny(corpus, "forg", "tamper", "cropped", "overlay", "signature")) {
                return issue;
            }
            if (containsAny(lower, "financial diversion", "unlawful enrichment", "theft") && containsAny(corpus, "invoice", "profit", "payment", "unpaid share", "goodwill")) {
                return issue;
            }
        }
        return issueGroups.get(0);
    }

    private static String normalizeOffenceLabel(String raw) {
        String lower = lowerUs(raw);
        if (lower.isEmpty()) {
            return "";
        }
        if (containsAny(lower, "fraud", "fraudulent purpose")) {
            return "fraud";
        }
        if (containsAny(lower, "cybercrime", "unauthorized access", "digital interference")) {
            return "unauthorized access or cyber interference";
        }
        if (containsAny(lower, "fiduciary")) {
            return "breach of fiduciary duty";
        }
        if (containsAny(lower, "shareholder oppression", "unlawful exclusion")) {
            return "shareholder oppression or unlawful exclusion";
        }
        if (containsAny(lower, "tamper", "spoliation", "fraudulent evidence", "forgery")) {
            return "evidence tampering or forgery";
        }
        if (containsAny(lower, "unlawful enrichment", "financial irregularity", "goodwill", "theft")) {
            return "unlawful enrichment or financial diversion";
        }
        return trimToEmpty(raw);
    }

    private static boolean containsAny(String corpus, String... terms) {
        String lower = lowerUs(corpus);
        for (String term : terms) {
            if (!trimToEmpty(term).isEmpty() && lower.contains(term.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private static String lowerUs(String value) {
        return trimToEmpty(value).toLowerCase(Locale.US);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String cleanNarrative(String value) {
        String trimmed = trimToEmpty(value)
                .replace('\n', ' ')
                .replace('\r', ' ');
        while (trimmed.contains("  ")) {
            trimmed = trimmed.replace("  ", " ");
        }
        trimmed = trimmed.replace("[truncated for on-device prompt]", "")
                .replace("...[truncated for on-device prompt]...", "")
                .replace("...[truncated for on-device prompt]", "")
                .trim();
        return clipText(trimmed, 220);
    }

    private static String ensureSentence(String value) {
        String cleaned = cleanNarrative(value);
        if (cleaned.isEmpty()) {
            return "";
        }
        char last = cleaned.charAt(cleaned.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return cleaned;
        }
        return cleaned + ".";
    }

    private static String clipText(String value, int limit) {
        String trimmed = trimToEmpty(value);
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, limit - 3)).trim() + "...";
    }

    private static String joinPages(List<Integer> pages) {
        List<String> labels = new ArrayList<>();
        if (pages != null) {
            for (Integer page : pages) {
                if (page != null && page > 0) {
                    labels.add(String.valueOf(page));
                }
            }
        }
        return String.join(", ", labels);
    }
}
