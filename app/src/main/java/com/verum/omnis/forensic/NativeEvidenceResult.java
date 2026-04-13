package com.verum.omnis.forensic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class NativeEvidenceResult {
    public String fileName;
    public String evidenceHash;
    public int pageCount;
    public int sourcePageCount;
    public boolean pdf;
    public int ocrSuccessCount;
    public int ocrFailedCount;
    public int renderedPageCount;
    public int renderLimit;
    public int documentTextPageCount;
    public int fastPathTextPageCount;
    public int visualOnlyPageCount;
    public boolean secondaryNarrativeSource;
    public boolean syntheticForensicSummary;
    public String pipelineStatus;
    public String renderError;
    public final List<OcrTextBlock> documentTextBlocks = new ArrayList<>();
    public final List<OcrTextBlock> ocrBlocks = new ArrayList<>();
    public final List<ExtractedProposition> propositions = new ArrayList<>();
    public final List<VisualForgeryFinding> visualFindings = new ArrayList<>();
    public final List<EvidenceAnchor> anchors = new ArrayList<>();
    public transient List<OcrTextBlock> sanitizedCorpusBlocks;
    public transient int sanitizedCorpusContaminatedBlockCount;
    public transient int sanitizedCorpusSecondaryNarrativeBlockCount;
    private static final List<String> GENERATED_ANALYSIS_MARKERS = Arrays.asList(
            "here's the clean forensic read of what you provided",
            "this intake reads as",
            "what the intake is really saying",
            "most important findings from your payload",
            "bottom-line forensic assessment",
            "best next move",
            "practical conclusion",
            "if i had to classify the evidentiary posture",
            "based only on the data you pasted",
            "clean, high-signal forensic intake",
            "i'll break it down in verum omnis terms",
            "i’ll break it down in verum omnis terms",
            "just what the data actually proves",
            "case status — verified state",
            "primary legal exposure",
            "core forensic finding",
            "final classification (verum omnis)",
            "bottom line",
            "if you want, next step i can do"
    );

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("fileName", fileName);
            root.put("evidenceHash", evidenceHash);
            root.put("pageCount", pageCount);
            root.put("sourcePageCount", sourcePageCount);
            root.put("isPdf", pdf);
            root.put("ocrSuccessCount", ocrSuccessCount);
            root.put("ocrFailedCount", ocrFailedCount);
            root.put("renderedPageCount", renderedPageCount);
            root.put("renderLimit", renderLimit);
            root.put("documentTextPageCount", documentTextPageCount);
            root.put("fastPathTextPageCount", fastPathTextPageCount);
            root.put("visualOnlyPageCount", visualOnlyPageCount);
            root.put("secondaryNarrativeSource", secondaryNarrativeSource);
            root.put("syntheticForensicSummary", syntheticForensicSummary);
            root.put("pipelineStatus", pipelineStatus);
            if (renderError != null && !renderError.trim().isEmpty()) {
                root.put("renderError", renderError);
            }

            JSONArray documentBlocks = new JSONArray();
            for (OcrTextBlock block : documentTextBlocks) {
                String sanitized = sanitizeForJson(block.text);
                if (sanitized.isEmpty()) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("page", block.pageIndex + 1);
                item.put("text", sanitized);
                item.put("confidence", block.confidence);
                documentBlocks.put(item);
            }
            root.put("documentTextBlocks", documentBlocks);

            JSONArray blocks = new JSONArray();
            for (OcrTextBlock block : ocrBlocks) {
                String sanitized = sanitizeForJson(block.text);
                if (sanitized.isEmpty()) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("page", block.pageIndex + 1);
                item.put("text", sanitized);
                item.put("confidence", block.confidence);
                blocks.put(item);
            }
            root.put("ocrBlocks", blocks);

            JSONArray visuals = new JSONArray();
            for (VisualForgeryFinding finding : visualFindings) {
                JSONObject item = new JSONObject();
                item.put("page", finding.pageIndex + 1);
                item.put("type", finding.type);
                item.put("severity", finding.severity);
                item.put("description", finding.description);
                item.put("region", finding.region);
                visuals.put(item);
            }
            root.put("visualFindings", visuals);

            JSONArray propositionArray = new JSONArray();
            for (ExtractedProposition proposition : propositions) {
                propositionArray.put(proposition.toJson());
            }
            root.put("propositionRegister", propositionArray);

            JSONArray anchorArray = new JSONArray();
            for (EvidenceAnchor anchor : anchors) {
                anchorArray.put(anchor.toJson());
            }
            root.put("anchors", anchorArray);
        } catch (JSONException e) {
            // This is unlikely to happen with standard usage, but required for compilation
            e.printStackTrace();
        }
        return root;
    }

    private static String sanitizeForJson(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty() || containsGeneratedAnalysis(normalized)) {
            return "";
        }
        return normalized;
    }

    private static boolean containsGeneratedAnalysis(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("---")
                || lower.contains("# ")
                || lower.contains("✅")
                || lower.contains("👉")) {
            return true;
        }
        for (String marker : GENERATED_ANALYSIS_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
