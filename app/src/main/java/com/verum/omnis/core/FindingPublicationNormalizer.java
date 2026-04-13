package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Keeps publication status separate from raw contradiction/finding status so the
 * reports follow the Constitution's publication rules instead of raw engine flags.
 */
public final class FindingPublicationNormalizer {

    public static final String PUBLICATION_CERTIFIED = "CERTIFIED";
    public static final String PUBLICATION_UNPUBLISHED = "UNPUBLISHED";
    public static final String PUBLICATION_NEEDS_REVIEW = "NEEDS_REVIEW";

    private FindingPublicationNormalizer() {}

    public static void applyToReport(AnalysisEngine.ForensicReport report) {
        if (report == null) {
            return;
        }
        JSONArray normalized = normalizeCertifiedFindings(report);
        report.normalizedCertifiedFindings = normalized;
        report.normalizedCertifiedFindingCount = countPublicationStatus(normalized, PUBLICATION_CERTIFIED);
        try {
            if (report.diagnostics == null) {
                report.diagnostics = new JSONObject();
            }
            report.diagnostics.put("normalizedCertifiedFindingCount", report.normalizedCertifiedFindingCount);
            report.diagnostics.put("publicationConsistencyErrors", new JSONArray(collectConsistencyErrors(report)));

            if (report.guardianDecision != null
                    && report.guardianDecision.optBoolean("approved", false)
                    && (!report.guardianDecision.has("approvedCount")
                    || report.guardianDecision.optInt("approvedCount", 0) <= 0)) {
                report.guardianDecision.put("approvedCount", report.normalizedCertifiedFindingCount);
            }

            if (report.tripleVerification != null) {
                JSONObject synthesis = report.tripleVerification.optJSONObject("synthesis");
                if (synthesis == null) {
                    synthesis = new JSONObject();
                    report.tripleVerification.put("synthesis", synthesis);
                }
                synthesis.put("certifiedFindingCount", report.normalizedCertifiedFindingCount);
            }
        } catch (JSONException ignored) {
        }
    }

    public static JSONArray normalizeCertifiedFindings(AnalysisEngine.ForensicReport report) {
        JSONArray normalized = new JSONArray();
        if (report == null || report.certifiedFindings == null) {
            return normalized;
        }
        boolean defaultGuardianApproval = report.guardianDecision != null
                && report.guardianDecision.optBoolean("approved", false);
        LinkedHashMap<String, JSONObject> deduped = new LinkedHashMap<>();
        for (int i = 0; i < report.certifiedFindings.length(); i++) {
            JSONObject certified = report.certifiedFindings.optJSONObject(i);
            if (certified == null) {
                continue;
            }
            JSONObject normalizedItem = normalizeFinding(certified, defaultGuardianApproval);
            if (normalizedItem == null) {
                continue;
            }
            String key = buildNormalizedFindingKey(normalizedItem, i);
            JSONObject existing = deduped.get(key);
            if (existing == null || shouldReplaceNormalizedFinding(existing, normalizedItem)) {
                deduped.put(key, normalizedItem);
            }
        }
        for (JSONObject item : deduped.values()) {
            normalized.put(item);
        }
        return normalized;
    }

    public static JSONArray renderableCertifiedFindings(AnalysisEngine.ForensicReport report) {
        applyToReport(report);
        JSONArray renderable = new JSONArray();
        if (report == null || report.normalizedCertifiedFindings == null) {
            return renderable;
        }
        for (int i = 0; i < report.normalizedCertifiedFindings.length(); i++) {
            JSONObject item = report.normalizedCertifiedFindings.optJSONObject(i);
            if (isPublicationCertified(item)) {
                renderable.put(item);
            }
        }
        return renderable;
    }

    public static boolean isPublicationCertified(JSONObject normalizedFinding) {
        if (normalizedFinding == null) {
            return false;
        }
        return PUBLICATION_CERTIFIED.equalsIgnoreCase(
                normalizedFinding.optString("publicationStatus", ""))
                && normalizedFinding.optBoolean("humanRenderable", false);
    }

    public static List<String> collectConsistencyErrors(AnalysisEngine.ForensicReport report) {
        List<String> errors = new ArrayList<>();
        if (report == null) {
            return errors;
        }
        int normalizedCount = report.normalizedCertifiedFindingCount;
        int guardianCount = report.guardianDecision != null
                ? report.guardianDecision.optInt("approvedCount", -1) : -1;
        JSONObject synthesis = report.tripleVerification != null
                ? report.tripleVerification.optJSONObject("synthesis") : null;
        int synthesisCount = synthesis != null
                ? synthesis.optInt("certifiedFindingCount", -1) : -1;
        if (guardianCount >= 0 && guardianCount != normalizedCount) {
            errors.add("guardianDecision.approvedCount disagrees with normalized certified finding count.");
        }
        if (synthesisCount >= 0 && synthesisCount != normalizedCount) {
            errors.add("tripleVerification.synthesis.certifiedFindingCount disagrees with normalized certified finding count.");
        }
        return errors;
    }

    public static List<String> collectRenderConsistencyErrors(
            AnalysisEngine.ForensicReport report,
            String renderedText
    ) {
        List<String> errors = new ArrayList<>();
        if (report == null) {
            return errors;
        }
        applyToReport(report);
        int normalizedCount = report.normalizedCertifiedFindingCount;
        String rendered = renderedText == null ? "" : renderedText;
        if (normalizedCount > 0) {
            if (rendered.contains("Certified findings: 0")
                    || rendered.contains("Guardian-approved certified findings: 0")) {
                errors.add("Output still says certified findings are zero.");
            }
            if (rendered.contains("No guardian-approved verified finding summary was available")) {
                errors.add("Output still uses the old guardian-approved verified fallback despite certified findings.");
            }
            if (rendered.contains("No guardian-approved certified finding summary was available")) {
                errors.add("Output still says no guardian-approved certified finding summary was available.");
            }
            if (rendered.contains("No certified findings were available for rendering in this pass.")) {
                errors.add("Output still says no certified findings were renderable.");
            }
            if (rendered.contains("Use the certified findings in section 3")
                    && (rendered.contains("\n3A. Certified Finding Blocks\nNo certified findings were available for rendering in this pass.")
                    || rendered.contains("\n3. Guardian-Approved Certified Findings\nNo guardian-approved certified findings survived the publication layer.\n"))) {
                errors.add("Output points the reader to section 3 while section 3 is empty.");
            }
        }
        return errors;
    }

    private static JSONObject normalizeFinding(JSONObject certified, boolean defaultGuardianApproval) {
        try {
            JSONObject normalized = new JSONObject(certified.toString());
            JSONObject certification = certified.optJSONObject("certification");
            JSONObject nestedFinding = certified.optJSONObject("finding");
            if (nestedFinding == null || nestedFinding.length() == 0) {
                nestedFinding = new JSONObject(certified.toString());
            } else {
                nestedFinding = new JSONObject(nestedFinding.toString());
            }

            String rawStatus = firstNonEmpty(
                    nestedFinding.optString("status", null),
                    certified.optString("status", null),
                    "UNSPECIFIED"
            );
            boolean guardianApproved = certification != null
                    ? certification.optBoolean("guardianApproval", defaultGuardianApproval)
                    : defaultGuardianApproval;
            String publicationStatus = guardianApproved ? PUBLICATION_CERTIFIED
                    : (hasRenderableContent(certified, nestedFinding) ? PUBLICATION_NEEDS_REVIEW : PUBLICATION_UNPUBLISHED);
            String contradictionStatus = firstNonEmpty(
                    nestedFinding.optString("contradictionStatus", null),
                    nestedFinding.optString("status", null),
                    certified.optString("status", null),
                    ""
            );
            String primarySummary = firstNonEmpty(
                    certified.optString("summary", null),
                    nestedFinding.optString("summary", null),
                    nestedFinding.optString("narrative", null),
                    nestedFinding.optString("whyItConflicts", null),
                    certified.optString("excerpt", null),
                    nestedFinding.optString("excerpt", null),
                    ""
            );
            int primaryPage = firstPositive(
                    certified.optInt("page", 0),
                    nestedFinding.optInt("page", 0),
                    firstAnchorPage(certified.optJSONArray("anchors")),
                    firstAnchorPage(nestedFinding.optJSONArray("anchors"))
            );
            JSONArray anchorPages = collectAnchorPages(certified.optJSONArray("anchors"), nestedFinding.optJSONArray("anchors"));
            JSONArray renderWarnings = new JSONArray();
            String actor = ActorNameNormalizer.canonicalizePublicationActor(firstNonEmpty(
                    nestedFinding.optString("actor", null),
                    certified.optString("actor", null),
                    ""
            ));
            String type = firstNonEmpty(
                    certified.optString("type", null),
                    nestedFinding.optString("findingType", null),
                    nestedFinding.optString("timelineType", null),
                    nestedFinding.optString("oversightType", null),
                    nestedFinding.optString("conflictType", null),
                    "CERTIFIED_FINDING"
            );

            if (primarySummary.isEmpty()) {
                renderWarnings.put("NEEDS_FIELD_NORMALIZATION");
            }
            if (actor.isEmpty() || looksLowReadabilityActor(actor)) {
                renderWarnings.put("LOW_READABILITY_ACTOR");
            }
            if (looksLowReadabilityAmount(primarySummary)) {
                renderWarnings.put("LOW_READABILITY_AMOUNT");
            }

            normalized.put("finding", nestedFinding);
            normalized.put("rawFindingStatus", rawStatus);
            normalized.put("guardianApproved", guardianApproved);
            normalized.put("publicationStatus", publicationStatus);
            normalized.put("contradictionStatus", contradictionStatus);
            normalized.put("humanRenderable", guardianApproved);
            normalized.put("renderWarnings", renderWarnings);
            normalized.put("primarySummary", primarySummary);
            if (primaryPage > 0) {
                normalized.put("primaryPage", primaryPage);
            } else {
                normalized.put("primaryPage", JSONObject.NULL);
            }
            normalized.put("anchorPages", anchorPages);
            if (!actor.isEmpty()) {
                normalized.put("actor", actor);
            }
            if (!type.isEmpty()) {
                normalized.put("type", type);
            }
            return normalized;
        } catch (JSONException e) {
            return null;
        }
    }
    private static String buildNormalizedFindingKey(JSONObject normalizedItem, int fallbackIndex) {
        if (normalizedItem == null) {
            return String.valueOf(fallbackIndex);
        }
        String actor = lowerUs(normalizedItem.optString("actor", ""));
        String summary = lowerUs(normalizedItem.optString("primarySummary", ""));
        String type = lowerUs(normalizedItem.optString("type", ""));
        JSONArray anchorPages = normalizedItem.optJSONArray("anchorPages");
        String anchors = anchorPages != null ? anchorPages.toString() : "[]";
        return actor + "|" + type + "|" + summary + "|" + anchors;
    }

    private static boolean shouldReplaceNormalizedFinding(JSONObject existing, JSONObject candidate) {
        if (existing == null) {
            return true;
        }
        if (candidate == null) {
            return false;
        }
        int existingScore = normalizedFindingScore(existing);
        int candidateScore = normalizedFindingScore(candidate);
        return candidateScore > existingScore;
    }

    private static int normalizedFindingScore(JSONObject item) {
        if (item == null) {
            return 0;
        }
        int score = 0;
        if (PUBLICATION_CERTIFIED.equalsIgnoreCase(item.optString("publicationStatus", ""))) {
            score += 4;
        }
        if (item.optBoolean("guardianApproved", false)) {
            score += 3;
        }
        if (!item.optString("primarySummary", "").trim().isEmpty()) {
            score += 2;
        }
        JSONArray pages = item.optJSONArray("anchorPages");
        if (pages != null) {
            score += Math.min(3, pages.length());
        }
        return score;
    }

    private static boolean hasRenderableContent(JSONObject certified, JSONObject finding) {
        return !firstNonEmpty(
                certified.optString("summary", null),
                finding.optString("summary", null),
                finding.optString("narrative", null),
                certified.optString("excerpt", null),
                finding.optString("excerpt", null),
                ""
        ).isEmpty()
                || !firstNonEmpty(finding.optString("actor", null), certified.optString("actor", null), "").isEmpty()
                || firstPositive(
                certified.optInt("page", 0),
                finding.optInt("page", 0),
                firstAnchorPage(certified.optJSONArray("anchors")),
                firstAnchorPage(finding.optJSONArray("anchors"))
        ) > 0;
    }
    private static String lowerUs(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
    private static int countPublicationStatus(JSONArray findings, String publicationStatus) {
        int count = 0;
        if (findings == null) {
            return 0;
        }
        for (int i = 0; i < findings.length(); i++) {
            JSONObject item = findings.optJSONObject(i);
            if (item != null && publicationStatus.equalsIgnoreCase(item.optString("publicationStatus", ""))) {
                count++;
            }
        }
        return count;
    }

    private static int firstAnchorPage(JSONArray anchors) {
        if (anchors == null) {
            return 0;
        }
        for (int i = 0; i < anchors.length(); i++) {
            JSONObject anchor = anchors.optJSONObject(i);
            if (anchor == null) {
                continue;
            }
            int page = anchor.optInt("page", 0);
            if (page > 0) {
                return page;
            }
        }
        return 0;
    }

    private static JSONArray collectAnchorPages(JSONArray primary, JSONArray secondary) {
        JSONArray pages = new JSONArray();
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        appendAnchorPages(seen, primary);
        appendAnchorPages(seen, secondary);
        for (Integer page : seen) {
            pages.put(page);
        }
        return pages;
    }

    private static void appendAnchorPages(LinkedHashSet<Integer> seen, JSONArray anchors) {
        if (anchors == null) {
            return;
        }
        for (int i = 0; i < anchors.length(); i++) {
            JSONObject anchor = anchors.optJSONObject(i);
            if (anchor == null) {
                continue;
            }
            int page = anchor.optInt("page", 0);
            if (page > 0) {
                seen.add(page);
            }
        }
    }

    private static boolean looksLowReadabilityActor(String actor) {
        String normalized = actor == null ? "" : actor.trim().toLowerCase(Locale.US);
        return normalized.isEmpty()
                || normalized.length() <= 2
                || normalized.equals("party")
                || normalized.equals("actor")
                || normalized.equals("person")
                || normalized.equals("company");
    }

    private static boolean looksLowReadabilityAmount(String summary) {
        String normalized = summary == null ? "" : summary.trim().toLowerCase(Locale.US);
        return normalized.contains("r 0")
                || normalized.contains("r01")
                || normalized.contains("amount unknown")
                || normalized.contains("amount disputed");
    }

    private static int firstPositive(int... values) {
        if (values == null) {
            return 0;
        }
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
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
}


