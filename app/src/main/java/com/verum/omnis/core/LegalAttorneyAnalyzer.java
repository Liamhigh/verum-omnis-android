package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class LegalAttorneyAnalyzer {

    private LegalAttorneyAnalyzer() {}

    public static JSONObject analyze(Context context, AnalysisEngine.ForensicReport report) {
        JSONObject result = new JSONObject();
        try {
            JSONObject jurisdiction = JurisdictionResolver.resolve(context, report);
            LegalCorpusRetriever.Retrieval retrieval = LegalCorpusRetriever.retrieveForReport(context, report, jurisdiction);

            putSafe(result, "jurisdiction", jurisdiction);
            putSafe(result, "retrieval", retrieval.manifest);
            putSafe(result, "corpusExcerpts", retrieval.excerpts);
            putSafe(result, "boundary", "Gemma may use sealed findings, jurisdiction context, and bundled legal-corpus excerpts only. Raw evidence is excluded.");

            String prompt = buildPrompt(report, jurisdiction, retrieval);
            String narrative = generateNarrative(context, prompt, retrieval, report, jurisdiction);

            putSafe(result, "promptSummary", buildPromptSummary(report, jurisdiction, retrieval));
            putSafe(result, "analysis", narrative);
            putSafe(result, "nextSteps", buildNextSteps(report, retrieval.excerpts, jurisdiction));
            putSafe(result, "confidence", deriveConfidence(report));
            putSafe(result, "mode", GemmaRuntime.getInstance().getStatus().toLowerCase(Locale.US).contains("ready")
                    ? "local-llm-assisted"
                    : "deterministic-fallback");
        } catch (Exception e) {
            putSafe(result, "analysis", "Legal attorney analysis could not be completed from the local corpus in this pass: " + e.getMessage());
            putSafe(result, "mode", "error");
            putSafe(result, "confidence", "LOW");
        }
        return result;
    }

    private static String generateNarrative(
            Context context,
            String prompt,
            LegalCorpusRetriever.Retrieval retrieval,
            AnalysisEngine.ForensicReport report,
            JSONObject jurisdiction
    ) {
        try {
            return GemmaRuntime.getInstance().generateResponseBlocking(context, prompt).trim();
        } catch (Throwable ignored) {
            return buildFallbackNarrative(report, retrieval, jurisdiction);
        }
    }

    private static String buildPrompt(
            AnalysisEngine.ForensicReport report,
            JSONObject jurisdiction,
            LegalCorpusRetriever.Retrieval retrieval
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are Gemma acting as the Verum Omnis legal attorney layer.\n");
        prompt.append("You are constitution-bound and offline.\n");
        prompt.append("You may use only the sealed findings, the jurisdiction result, and the bundled legal corpus excerpts provided below.\n");
        prompt.append("Do not inspect raw evidence. Do not invent facts, law, dates, actors, or forum outcomes.\n");
        prompt.append("Use ordinal confidence only: VERY_HIGH, HIGH, MODERATE, LOW, INSUFFICIENT.\n");
        prompt.append("Separate factual findings, legal pathways, and unresolved gaps.\n\n");
        prompt.append("Jurisdiction:\n").append(jurisdiction.toString()).append("\n\n");
        prompt.append("Sealed findings summary:\n").append(buildPromptSummary(report, jurisdiction, retrieval)).append("\n\n");
        prompt.append("Bundled legal corpus excerpts:\n").append(buildExcerptDigest(retrieval.excerpts)).append("\n\n");
        prompt.append("Output short plain text under these headings exactly:\n");
        prompt.append("LEGAL ATTORNEY ANALYSIS\n");
        prompt.append("OFFENCE FRAMEWORKS\n");
        prompt.append("NEXT PROCEDURAL STEPS\n");
        prompt.append("GAPS AND LIMITATIONS\n");
        return prompt.toString();
    }

    private static String buildPromptSummary(
            AnalysisEngine.ForensicReport report,
            JSONObject jurisdiction,
            LegalCorpusRetriever.Retrieval retrieval
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Case ID: ").append(report != null ? safe(report.caseId) : "").append('\n');
        sb.append("Jurisdiction code: ").append(jurisdiction != null ? jurisdiction.optString("code", "") : "").append('\n');
        sb.append("Jurisdiction name: ").append(jurisdiction != null ? jurisdiction.optString("name", "") : "").append('\n');
        sb.append("Summary: ").append(report != null ? safe(report.summary) : "").append('\n');
        sb.append("Top liabilities: ").append(join(report != null ? report.topLiabilities : null)).append('\n');
        sb.append("Legal references: ").append(join(report != null ? report.legalReferences : null)).append('\n');
        sb.append("Certified findings count: ")
                .append(report != null ? report.normalizedCertifiedFindingCount : 0)
                .append('\n');
        sb.append("Retrieved legal excerpts: ").append(retrieval != null ? retrieval.excerpts.length() : 0);
        return sb.toString().trim();
    }

    private static JSONArray buildNextSteps(
            AnalysisEngine.ForensicReport report,
            JSONArray excerpts,
            JSONObject jurisdiction
    ) {
        JSONArray nextSteps = new JSONArray();
        String jurisdictionName = jurisdiction != null ? jurisdiction.optString("name", "the relevant forum") : "the relevant forum";
        put(nextSteps, "Keep the strongest certified findings, contradiction posture, and page anchors together in one short " + jurisdictionName + " handoff pack.");
        for (int i = 0; i < excerpts.length() && nextSteps.length() < 4; i++) {
            JSONObject item = excerpts.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String text = item.optString("text", "").trim();
            if (!text.isEmpty()) {
                put(nextSteps, text);
            }
        }
        if (report != null && report.tripleVerification != null) {
            JSONObject anti = report.tripleVerification.optJSONObject("antithesis");
            if (anti != null && anti.optInt("verifiedCount", 0) == 0 && anti.optInt("candidateCount", 0) > 0) {
                put(nextSteps, "Keep any contradiction-led actor conclusion provisional until a verified contradiction matures.");
            }
        }
        return nextSteps;
    }

    private static String deriveConfidence(AnalysisEngine.ForensicReport report) {
        if (report != null && report.tripleVerification != null) {
            JSONObject synthesis = report.tripleVerification.optJSONObject("synthesis");
            if (synthesis != null && synthesis.optInt("certifiedFindingCount", 0) >= 3) {
                return "HIGH";
            }
        }
        if (report != null && report.normalizedCertifiedFindingCount > 0) {
            return "MODERATE";
        }
        return "LOW";
    }

    private static String buildFallbackNarrative(
            AnalysisEngine.ForensicReport report,
            LegalCorpusRetriever.Retrieval retrieval,
            JSONObject jurisdiction
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("LEGAL ATTORNEY ANALYSIS\n");
        sb.append("This legal-attorney view was prepared from sealed findings and the bundled legal corpus only. ");
        sb.append("It does not inspect raw evidence and should be read as a governed legal pathway note, not as a substitute for the sealed forensic findings.\n\n");
        sb.append("OFFENCE FRAMEWORKS\n");
        if (report != null && report.topLiabilities != null && report.topLiabilities.length > 0) {
            sb.append("The strongest current pathway is: ").append(report.topLiabilities[0]).append(". ");
        }
        if (retrieval != null && retrieval.excerpts.length() > 0) {
            JSONObject item = retrieval.excerpts.optJSONObject(0);
            if (item != null) {
                sb.append(item.optString("text", ""));
            }
        }
        sb.append("\n\nNEXT PROCEDURAL STEPS\n");
        JSONArray nextSteps = buildNextSteps(report, retrieval != null ? retrieval.excerpts : new JSONArray(), jurisdiction);
        for (int i = 0; i < nextSteps.length(); i++) {
            sb.append("- ").append(nextSteps.optString(i)).append('\n');
        }
        sb.append("\nGAPS AND LIMITATIONS\n");
        sb.append("This layer remains downstream from the forensic engine. It should not overstate verified contradictions, liability, or forum outcomes beyond what the sealed findings already support.");
        return sb.toString().trim();
    }

    private static void put(JSONArray array, String value) {
        String cleaned = safe(value);
        if (!cleaned.isEmpty()) {
            array.put(cleaned);
        }
    }

    private static String buildExcerptDigest(JSONArray excerpts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < excerpts.length() && i < 4; i++) {
            JSONObject item = excerpts.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("[")
                    .append(safe(item.optString("bucket", "legal")))
                    .append("] ")
                    .append(safe(item.optString("title", "Untitled excerpt")))
                    .append("\n")
                    .append(clip(safe(item.optString("text", "")), 360));
        }
        return sb.toString().trim();
    }

    private static String join(String[] items) {
        if (items == null || items.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            String cleaned = safe(item);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(cleaned);
        }
        return sb.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value, int maxChars) {
        String cleaned = safe(value);
        if (cleaned.length() <= Math.max(1, maxChars)) {
            return cleaned;
        }
        return cleaned.substring(0, Math.max(1, maxChars)).trim() + " …";
    }

    private static void putSafe(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
