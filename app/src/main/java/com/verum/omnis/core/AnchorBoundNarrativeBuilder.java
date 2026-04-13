package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class AnchorBoundNarrativeBuilder {

    public static final class Narrative {
        public String summary = "";
        public List<String> keyFindings = new ArrayList<>();
        public List<String> timelineHighlights = new ArrayList<>();
        public String implicationSummary = "";
        public String proofBoundary = "";
    }

    private AnchorBoundNarrativeBuilder() {}

    public static Narrative build(JSONObject forensicConclusion, JSONObject truthFrame) {
        Narrative out = new Narrative();
        JSONObject safeConclusion = forensicConclusion != null ? forensicConclusion : new JSONObject();
        JSONObject safeTruthFrame = truthFrame != null ? truthFrame : new JSONObject();

        List<String> propositionLines = collectPropositionLines(safeConclusion, true);
        List<String> propositionBullets = collectPropositionLines(safeConclusion, false);
        String truthSummary = ensureSentence(trimToEmpty(safeTruthFrame.optString("whatHappened", "")));

        out.summary = firstNonEmpty(truthSummary, firstOf(propositionLines));
        if (out.summary.isEmpty()) {
            out.summary = "The sealed record does not yet compress into one cleaner summary line without overstating the evidence.";
        }

        out.keyFindings.addAll(limit(propositionBullets, 3));
        if (out.keyFindings.isEmpty()) {
            JSONArray proven = safeConclusion.optJSONArray("certifiedForensicConduct");
            if (proven != null) {
                for (int i = 0; i < proven.length() && out.keyFindings.size() < 3; i++) {
                    String line = ensureSentence(trimToEmpty(proven.optString(i, "")));
                    if (!line.isEmpty()) {
                        out.keyFindings.add(line);
                    }
                }
            }
        }

        JSONArray when = safeTruthFrame.optJSONArray("when");
        if (when != null) {
            for (int i = 0; i < when.length() && out.timelineHighlights.size() < 3; i++) {
                String line = ensureSentence(trimToEmpty(when.optString(i, "")));
                if (!line.isEmpty()) {
                    out.timelineHighlights.add(line);
                }
            }
        }

        out.implicationSummary = buildImplicationSummary(safeConclusion);
        out.proofBoundary = ensureSentence(trimToEmpty(safeConclusion.optString("publicationBoundary", "")));
        if (out.proofBoundary.isEmpty()) {
            out.proofBoundary = "This is a forensic conclusion, not a judicial verdict.";
        }
        return out;
    }

    private static List<String> collectPropositionLines(JSONObject forensicConclusion, boolean withoutPages) {
        List<String> out = new ArrayList<>();
        JSONArray propositions = forensicConclusion.optJSONArray("forensicPropositions");
        if (propositions == null) {
            return out;
        }
        for (int i = 0; i < propositions.length(); i++) {
            JSONObject proposition = propositions.optJSONObject(i);
            if (proposition == null) {
                continue;
            }
            String actor = trimToEmpty(proposition.optString("actor", ""));
            String conduct = trimToEmpty(proposition.optString("conduct", ""));
            JSONArray pages = proposition.optJSONArray("anchorPages");
            if (actor.isEmpty() || conduct.isEmpty() || pages == null || pages.length() == 0) {
                continue;
            }
            String line = actor + " is linked in the sealed record to " + stripTerminalPunctuation(conduct) + ".";
            if (!withoutPages) {
                String pageText = joinPages(pages);
                if (!pageText.isEmpty()) {
                    line += " Pages: " + pageText + ".";
                }
            }
            out.add(line);
        }
        return dedupe(out);
    }

    private static String buildImplicationSummary(JSONObject forensicConclusion) {
        JSONArray actors = forensicConclusion.optJSONArray("implicatedActors");
        if (actors == null) {
            return "";
        }
        String primary = "";
        String affected = "";
        List<String> linked = new ArrayList<>();
        for (int i = 0; i < actors.length(); i++) {
            JSONObject actor = actors.optJSONObject(i);
            if (actor == null) {
                continue;
            }
            String name = trimToEmpty(actor.optString("actor", ""));
            String role = trimToEmpty(actor.optString("role", ""));
            if (name.isEmpty()) {
                continue;
            }
            if ("PRIMARY_IMPLICATED".equalsIgnoreCase(role) && primary.isEmpty()) {
                primary = name;
            } else if ("AFFECTED_PARTY".equalsIgnoreCase(role) && affected.isEmpty()) {
                affected = name;
            } else if (!linked.contains(name)) {
                linked.add(name);
            }
        }
        if (!primary.isEmpty() && !affected.isEmpty()) {
            return "At this stage, the record points mainly to " + primary
                    + ", while " + affected
                    + " is the clearest affected party carried in the same anchored material.";
        }
        if (!primary.isEmpty()) {
            return "At this stage, the record points mainly to " + primary + ".";
        }
        if (!affected.isEmpty()) {
            return affected + " is the clearest affected party carried in this pass.";
        }
        if (!linked.isEmpty()) {
            return "Other linked actors already visible in the same anchored material include "
                    + joinStrings(limit(linked, 3)) + ".";
        }
        return "";
    }

    private static List<String> dedupe(List<String> source) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (source != null) {
            for (String item : source) {
                String line = ensureSentence(trimToEmpty(item));
                if (!line.isEmpty()) {
                    seen.add(line);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    private static List<String> limit(List<String> source, int max) {
        List<String> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (String item : source) {
            if (trimToEmpty(item).isEmpty()) {
                continue;
            }
            out.add(item);
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    private static String joinPages(JSONArray pages) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < pages.length(); i++) {
            int page = pages.optInt(i, 0);
            if (page > 0) {
                values.add(String.valueOf(page));
            }
        }
        return joinStrings(values);
    }

    private static String joinStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " and " + values.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(i == values.size() - 1 ? ", and " : ", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static String firstNonEmpty(String first, String second) {
        if (!trimToEmpty(first).isEmpty()) {
            return first;
        }
        return trimToEmpty(second).isEmpty() ? "" : second;
    }

    private static String firstOf(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return trimToEmpty(values.get(0));
    }

    private static String ensureSentence(String value) {
        String cleaned = trimToEmpty(value);
        if (cleaned.isEmpty()) {
            return "";
        }
        char last = cleaned.charAt(cleaned.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return cleaned;
        }
        return cleaned + ".";
    }

    private static String stripTerminalPunctuation(String value) {
        String cleaned = trimToEmpty(value);
        while (!cleaned.isEmpty()) {
            char last = cleaned.charAt(cleaned.length() - 1);
            if (last == '.' || last == '!' || last == '?') {
                cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
                continue;
            }
            break;
        }
        return cleaned;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
