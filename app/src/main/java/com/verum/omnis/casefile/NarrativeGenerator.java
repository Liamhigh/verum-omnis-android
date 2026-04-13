package com.verum.omnis.casefile;

import android.content.Context;

import com.verum.omnis.core.GemmaRuntime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NarrativeGenerator {
    private static final boolean ENABLE_GEMMA_CASEFILE_NARRATIVE = false;
    private final Context context;

    public NarrativeGenerator(Context context) {
        this.context = context.getApplicationContext();
    }

    public String generateNarrative(File mergedForensicJson) throws Exception {
        String jsonContent;
        try (FileInputStream fis = new FileInputStream(mergedForensicJson)) {
            jsonContent = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!ENABLE_GEMMA_CASEFILE_NARRATIVE) {
            return buildFallbackNarrative(jsonContent);
        }
        String prompt = "You are a forensic case writer for Verum Omnis.\n"
                + "Write a concise but court-usable case bundle index from the forensic report below.\n"
                + "Use only the facts in the report. Do not invent facts, actors, jurisdictions, or legal outcomes.\n"
                + "Explicitly name the primary actors and institutions when they appear in the report.\n"
                + "Explicitly identify all jurisdictions or countries implicated by the report.\n"
                + "Treat the indexed PDFs as preserved source exhibits. Do not imply that their bytes were altered.\n"
                + "Where findings include page anchors, mention the page numbers.\n"
                + "Structure the response with these headings exactly:\n"
                + "Case Narrative\nPreserved Source PDFs\nJurisdiction and Offence Map\nVerified Findings\nCandidate Findings\nRecommended Actions\n\n"
                + jsonContent;
        String response = GemmaRuntime.getInstance().generateResponseBlocking(context, prompt);
        if (response != null && !response.trim().isEmpty()) {
            return response.trim();
        }
        return buildFallbackNarrative(jsonContent);
    }

    private String buildFallbackNarrative(String jsonContent) {
        try {
            JSONObject root = new JSONObject(jsonContent);
            StringBuilder sb = new StringBuilder();
            boolean recoveryMode = root.optBoolean("mergeRecoveryMode", false);
            sb.append("Case Narrative\n");
            sb.append("Case ").append(root.optString("caseId", "unknown"))
                    .append(" was assembled into a merged evidence set under the Verum Omnis protocol. ");
            sb.append(root.optString("summary", "No merged summary was available.")).append(" ");
            String jurisdiction = firstNonBlank(
                    root.optString("jurisdictionName", ""),
                    root.optString("jurisdiction", "")
            );
            if (!jurisdiction.isEmpty()) {
                sb.append("Jurisdiction scope: ").append(jurisdiction).append(". ");
            }
            if (recoveryMode) {
                sb.append("The case writer is operating in recovery mode, so the indexed source PDFs remain the primary record until the merged narrative path is fully resolved. ");
            }
            sb.append("\n\n");

            JSONObject evidenceManifest = root.optJSONObject("evidenceManifest");
            JSONArray indexedPdfs = preferredManifestPdfArray(evidenceManifest);
            sb.append("Preserved Source PDFs\n");
            if (indexedPdfs != null && indexedPdfs.length() > 0) {
                for (int i = 0; i < indexedPdfs.length() && i < 8; i++) {
                    JSONObject item = indexedPdfs.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    sb.append("- ").append(item.optString("name", "source.pdf"))
                            .append(" | ").append(formatKilobytes(item.optLong("sizeBytes", 0L)))
                            .append(" | SHA-512 ").append(item.optString("sha512", "HASH_ERROR"))
                            .append("\n");
                }
            } else {
                sb.append("- No indexed PDF sources were recorded in the merged case manifest.\n");
            }
            sb.append("\n");

            sb.append("Jurisdiction and Offence Map\n");
            JSONArray findingsIndex = root.optJSONArray("findings");
            if (findingsIndex == null || findingsIndex.length() == 0) {
                sb.append("- No offence index was available in the merged record.\n");
            } else {
                List<String> offenceLines = new ArrayList<>();
                for (int i = 0; i < findingsIndex.length() && offenceLines.size() < 8; i++) {
                    JSONObject item = findingsIndex.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String actor = firstNonBlank(item.optString("actor", ""), "unresolved actor");
                    String summary = firstNonBlank(
                            item.optString("summary", ""),
                            item.optString("excerpt", ""),
                            item.optString("type", "Finding")
                    );
                    int page = item.optInt("page", 0);
                    String line = actor + ": " + summary + (page > 0 ? " | page " + page : "");
                    if (!offenceLines.contains(line)) {
                        offenceLines.add(line);
                    }
                }
                if (offenceLines.isEmpty()) {
                    sb.append("- No offence index was available in the merged record.\n");
                } else {
                    for (String line : offenceLines) {
                        sb.append("- ").append(line).append("\n");
                    }
                }
            }
            sb.append("\n");

            sb.append("Verified Findings\n");
            JSONArray findings = root.optJSONArray("certifiedFindings");
            if (findings == null || findings.length() == 0) {
                sb.append("- No guardian-approved certified findings were present in the merged record.\n");
            } else {
                for (int i = 0; i < findings.length() && i < 6; i++) {
                    JSONObject item = findings.optJSONObject(i);
                    JSONObject finding = item != null ? item.optJSONObject("finding") : null;
                    if (finding == null) {
                        continue;
                    }
                    sb.append("- ").append(firstNonBlank(
                            finding.optString("summary", ""),
                            finding.optString("whyItConflicts", ""),
                            finding.optString("excerpt", ""),
                            finding.optString("findingType", "Certified finding")
                    )).append("\n");
                }
            }

            sb.append("\nCandidate Findings\n");
            JSONArray mergedFindings = root.optJSONArray("findings");
            if (mergedFindings == null || mergedFindings.length() == 0) {
                sb.append("- No candidate finding summary was available.\n");
            } else {
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < mergedFindings.length() && lines.size() < 6; i++) {
                    JSONObject item = mergedFindings.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String line = firstNonBlank(
                            item.optString("summary", ""),
                            item.optString("excerpt", ""),
                            item.optString("type", "")
                    );
                    if (!line.isEmpty() && !lines.contains(line)) {
                        lines.add(line);
                    }
                }
                if (lines.isEmpty()) {
                    sb.append("- No candidate finding summary was available.\n");
                } else {
                    for (String line : lines) {
                        sb.append("- ").append(line).append("\n");
                    }
                }
            }

            sb.append("\nRecommended Actions\n");
            JSONArray liabilities = root.optJSONArray("topLiabilities");
            if (liabilities == null || liabilities.length() == 0) {
                sb.append("- Review the merged forensic JSON and sealed audit materials before external use.\n");
            } else {
                for (int i = 0; i < liabilities.length() && i < 4; i++) {
                    String liability = liabilities.optString(i, "").trim();
                    if (!liability.isEmpty()) {
                        sb.append("- Review the evidence supporting ").append(liability).append(" against the sealed merged record.\n");
                    }
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Case Narrative\nA merged forensic case file was created, but the narrative generator could not produce a structured summary.\n\n"
                    + "Recommended Actions\n- Review the merged forensic JSON and sealed case metadata directly.\n";
        }
    }

    private JSONArray preferredManifestPdfArray(JSONObject evidenceManifest) {
        if (evidenceManifest == null) {
            return null;
        }
        JSONArray evidenceFiles = evidenceManifest.optJSONArray("evidenceFiles");
        JSONArray filteredEvidenceFiles = filterSourcePdfItems(evidenceFiles);
        if (filteredEvidenceFiles != null && filteredEvidenceFiles.length() > 0) {
            return filteredEvidenceFiles;
        }
        JSONArray indexedPdfs = evidenceManifest.optJSONArray("indexedPdfs");
        return filterSourcePdfItems(indexedPdfs);
    }

    private JSONArray filterSourcePdfItems(JSONArray items) {
        if (items == null) {
            return null;
        }
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String name = item.optString("name", "").trim();
            if (!isSourcePdfName(name)) {
                continue;
            }
            filtered.put(item);
        }
        return filtered;
    }

    private boolean isSourcePdfName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.trim().toLowerCase(java.util.Locale.US);
        if (!lower.endsWith(".pdf")) {
            return false;
        }
        return !lower.contains("sealed-evidence")
                && !lower.contains("forensic-audit-report")
                && !lower.contains("human-forensic-report")
                && !lower.contains("readable-forensic-brief")
                && !lower.contains("constitutional-vault-report")
                && !lower.contains("sealed_narrative")
                && !lower.contains("sealed_case_bundle")
                && !lower.contains("legal-advisory")
                && !lower.contains("visual-findings");
    }

    private static String formatKilobytes(long sizeBytes) {
        if (sizeBytes <= 0) {
            return "0 KB";
        }
        return String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0d);
    }

    private static String firstNonBlank(String... values) {
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
