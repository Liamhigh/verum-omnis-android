package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LegalCorpusRetriever {

    public static final class Retrieval {
        public final JSONObject manifest;
        public final JSONArray excerpts;
        public final String combinedText;

        Retrieval(JSONObject manifest, JSONArray excerpts, String combinedText) {
            this.manifest = manifest;
            this.excerpts = excerpts;
            this.combinedText = combinedText;
        }
    }

    private LegalCorpusRetriever() {}

    public static Retrieval retrieveForReport(
            Context context,
            AnalysisEngine.ForensicReport report,
            JSONObject jurisdictionResolution
    ) {
        try {
            String jurisdictionCode = jurisdictionResolution != null
                    ? jurisdictionResolution.optString("code", safe(report != null ? report.jurisdiction : "MULTI"))
                    : safe(report != null ? report.jurisdiction : "MULTI");
            if (jurisdictionCode.isEmpty()) {
                jurisdictionCode = "MULTI";
            }

            String corpus = buildNeedleCorpus(report, jurisdictionResolution);
            JSONArray excerpts = new JSONArray();
            appendRankedItems(excerpts, loadOffenceFrameworks(context), corpus, "authority_constitution", 3);
            appendRankedItems(excerpts, loadJurisdictionItems(context, jurisdictionCode, "statutes"), corpus, "authority_constitution", 3);
            appendRankedItems(excerpts, loadJurisdictionItems(context, jurisdictionCode, "procedural_rules"), corpus, "authority_constitution", 2);
            appendRankedItems(excerpts, loadJurisdictionItems(context, jurisdictionCode, "precedent_summaries"), corpus, "case_history_patterns", 2);
            appendStyleExamples(excerpts, context, jurisdictionCode, corpus, 2);

            JSONObject manifest = new JSONObject();
            putSafe(manifest, "jurisdiction", jurisdictionCode);
            putSafe(manifest, "source", "bundled-assets");
            putSafe(manifest, "excerptCount", excerpts.length());

            return new Retrieval(manifest, excerpts, buildCombinedText(excerpts));
        } catch (Exception e) {
            JSONObject manifest = new JSONObject();
            putSafe(manifest, "jurisdiction", jurisdictionResolution != null ? jurisdictionResolution.optString("code", "MULTI") : "MULTI");
            putSafe(manifest, "source", "bundled-assets");
            putSafe(manifest, "error", e.getMessage());
            return new Retrieval(manifest, new JSONArray(), "");
        }
    }

    private static String buildCombinedText(JSONArray excerpts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < excerpts.length(); i++) {
            JSONObject item = excerpts.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("[").append(item.optString("bucket", "legal")).append("] ");
            sb.append(item.optString("title", "Untitled excerpt")).append("\n");
            sb.append(item.optString("text", ""));
        }
        return sb.toString().trim();
    }

    private static void appendStyleExamples(
            JSONArray target,
            Context context,
            String jurisdictionCode,
            String corpus,
            int limit
    ) throws Exception {
        String[] lines = RulesProvider.getLegalPackGemmaStyleExamples(context).split("\\r?\\n");
        List<JSONObject> matches = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            JSONObject item = new JSONObject(trimmed);
            JSONObject input = item.optJSONObject("input");
            String jurisdiction = input != null ? input.optString("jurisdiction", "") : "";
            if (!jurisdiction.isEmpty() && !"MULTI".equalsIgnoreCase(jurisdictionCode) && !jurisdiction.equalsIgnoreCase(jurisdictionCode)) {
                continue;
            }
            JSONObject excerpt = new JSONObject();
            putSafe(excerpt, "bucket", "report_style_examples");
            putSafe(excerpt, "title", item.optString("id", "style-example"));
            putSafe(excerpt, "text", item.optString("output", ""));
            putSafe(excerpt, "score", scoreText(corpus, item.toString()) + 25);
            matches.add(excerpt);
        }
        matches.sort(Comparator.comparingInt(o -> -o.optInt("score", 0)));
        appendTopUnique(target, matches, limit);
    }

    private static void appendRankedItems(
            JSONArray target,
            JSONArray sourceItems,
            String corpus,
            String bucket,
            int limit
    ) {
        List<JSONObject> ranked = new ArrayList<>();
        for (int i = 0; i < sourceItems.length(); i++) {
            JSONObject item = sourceItems.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String text = item.optString("text", "");
            String title = item.optString("title", item.optString("id", "untitled"));
            int rank = item.optInt("rank", 0);
            int score = rank + scoreText(corpus, title + "\n" + text);
            if (score <= 0) {
                continue;
            }
            JSONObject excerpt = new JSONObject();
            putSafe(excerpt, "bucket", bucket);
            putSafe(excerpt, "title", title);
            putSafe(excerpt, "text", text);
            putSafe(excerpt, "source", item.optString("source", ""));
            putSafe(excerpt, "score", score);
            ranked.add(excerpt);
        }
        ranked.sort(Comparator.comparingInt(o -> -o.optInt("score", 0)));
        appendTopUnique(target, ranked, limit);
    }

    private static void appendTopUnique(JSONArray target, List<JSONObject> ranked, int limit) {
        Set<String> seen = new LinkedHashSet<>();
        int count = 0;
        for (JSONObject item : ranked) {
            String key = item.optString("bucket", "") + "|" + item.optString("title", "");
            if (!seen.add(key)) {
                continue;
            }
            target.put(item);
            count++;
            if (count >= limit) {
                break;
            }
        }
    }

    private static JSONArray loadOffenceFrameworks(Context context) throws Exception {
        JSONObject root = new JSONObject(RulesProvider.getLegalPackOffenceFrameworks(context));
        return root.optJSONArray("frameworks") != null ? root.optJSONArray("frameworks") : new JSONArray();
    }

    private static JSONArray loadJurisdictionItems(Context context, String jurisdictionCode, String type) throws Exception {
        JSONObject root = new JSONObject(RulesProvider.getLegalPackJurisdictionRules(context));
        JSONObject jurisdictions = root.optJSONObject("jurisdictions");
        if (jurisdictions == null) {
            return new JSONArray();
        }
        if ("MULTI".equalsIgnoreCase(jurisdictionCode)) {
            JSONArray combined = new JSONArray();
            appendAll(combined, loadJurisdictionItems(context, "UAE", type));
            appendAll(combined, loadJurisdictionItems(context, "ZAF", type));
            return combined;
        }
        JSONObject entry = jurisdictions.optJSONObject(jurisdictionCode.toUpperCase(Locale.US));
        if (entry == null) {
            return new JSONArray();
        }
        String relativePath = entry.optString(type, "");
        if (relativePath.isEmpty()) {
            return new JSONArray();
        }
        JSONObject file = new JSONObject(RulesProvider.getLegalPackAsset(context, relativePath));
        return file.optJSONArray("items") != null ? file.optJSONArray("items") : new JSONArray();
    }

    private static void appendAll(JSONArray target, JSONArray source) {
        for (int i = 0; i < source.length(); i++) {
            target.put(source.optJSONObject(i));
        }
    }

    private static int scoreText(String corpus, String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) {
            return 0;
        }
        String lowerCorpus = safe(corpus).toLowerCase(Locale.US);
        String lowerCandidate = candidate.toLowerCase(Locale.US);
        int score = 0;

        Set<String> keywords = extractKeywords(lowerCorpus);
        for (String keyword : keywords) {
            if (lowerCandidate.contains(keyword)) {
                score += 6;
            }
        }
        if (lowerCandidate.contains("shareholder") && lowerCorpus.contains("shareholder")) score += 10;
        if (lowerCandidate.contains("cyber") && (lowerCorpus.contains("access") || lowerCorpus.contains("account"))) score += 10;
        if (lowerCandidate.contains("document") && (lowerCorpus.contains("signature") || lowerCorpus.contains("unsigned"))) score += 10;
        return score;
    }

    private static Set<String> extractKeywords(String text) {
        String[] seed = new String[] {
                "shareholder", "oppression", "exclusion", "cyber", "access", "account", "password",
                "fraud", "forgery", "signature", "unsigned", "lease", "renewal", "goodwill",
                "payment", "profit", "company", "director", "agreement", "deal", "export", "email"
        };
        Set<String> keywords = new LinkedHashSet<>();
        for (String keyword : seed) {
            if (text.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    private static String buildNeedleCorpus(AnalysisEngine.ForensicReport report, JSONObject jurisdictionResolution) {
        StringBuilder sb = new StringBuilder();
        if (report != null) {
            append(sb, report.summary);
            append(sb, report.jurisdiction);
            append(sb, report.jurisdictionName);
            if (report.topLiabilities != null) for (String item : report.topLiabilities) append(sb, item);
            if (report.legalReferences != null) for (String item : report.legalReferences) append(sb, item);
            if (report.normalizedCertifiedFindings != null) append(sb, report.normalizedCertifiedFindings.toString());
            else if (report.certifiedFindings != null) append(sb, report.certifiedFindings.toString());
            if (report.forensicSynthesis != null) append(sb, report.forensicSynthesis.toString());
            if (report.tripleVerification != null) append(sb, report.tripleVerification.toString());
        }
        if (jurisdictionResolution != null) append(sb, jurisdictionResolution.toString());
        return sb.toString();
    }

    private static void append(StringBuilder sb, String text) {
        String cleaned = safe(text);
        if (!cleaned.isEmpty()) {
            sb.append(cleaned).append('\n');
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static void putSafe(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
