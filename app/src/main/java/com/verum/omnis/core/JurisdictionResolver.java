package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class JurisdictionResolver {

    private JurisdictionResolver() {}

    public static JSONObject resolve(Context context, AnalysisEngine.ForensicReport report) {
        JSONObject result = new JSONObject();
        try {
            String reportCode = safe(report != null ? report.jurisdiction : "");
            String reportName = safe(report != null ? report.jurisdictionName : "");
            String anchor = safe(report != null ? report.jurisdictionAnchor : "");

            String normalizedCode = normalizeCode(reportCode);
            String source = normalizedCode.isEmpty() ? "fallback" : "sealed-report";
            String rationale = buildRationale(report, normalizedCode, reportName, anchor);

            if (normalizedCode.isEmpty()) {
                normalizedCode = "MULTI";
                if (reportName.isEmpty()) {
                    reportName = "Multi-jurisdiction";
                }
            }

            putSafe(result, "code", normalizedCode);
            putSafe(result, "name", !reportName.isEmpty() ? reportName : displayNameFor(normalizedCode));
            putSafe(result, "source", source);
            putSafe(result, "anchor", anchor);
            putSafe(result, "rationale", rationale);
            putSafe(result, "resolvedFrom", buildResolvedFrom(report));
        } catch (Exception e) {
            putSafe(result, "code", "MULTI");
            putSafe(result, "name", "Multi-jurisdiction");
            putSafe(result, "source", "fallback");
            putSafe(result, "anchor", "");
            putSafe(result, "rationale", "Jurisdiction resolver fallback was used because the sealed report fields were incomplete.");
            putSafe(result, "resolvedFrom", new JSONArray());
        }
        return result;
    }

    private static JSONArray buildResolvedFrom(AnalysisEngine.ForensicReport report) {
        JSONArray sources = new JSONArray();
        if (report == null) {
            return sources;
        }
        addIfPresent(sources, report.jurisdiction);
        addIfPresent(sources, report.jurisdictionName);
        addIfPresent(sources, report.jurisdictionAnchor);
        if (report.legalReferences != null) {
            for (String ref : report.legalReferences) {
                addIfPresent(sources, ref);
            }
        }
        return sources;
    }

    private static void addIfPresent(JSONArray array, String value) {
        String cleaned = safe(value);
        if (!cleaned.isEmpty()) {
            array.put(cleaned);
        }
    }

    private static String buildRationale(
            AnalysisEngine.ForensicReport report,
            String code,
            String name,
            String anchor
    ) {
        StringBuilder sb = new StringBuilder();
        if (!code.isEmpty()) {
            sb.append("Primary jurisdiction code from the sealed report is ").append(code).append(". ");
        }
        if (!name.isEmpty()) {
            sb.append("Display name: ").append(name).append(". ");
        }
        if (!anchor.isEmpty()) {
            sb.append("Anchor: ").append(anchor).append(". ");
        }
        if (report != null && report.legalReferences != null && report.legalReferences.length > 0) {
            sb.append("Legal references in the sealed report support this forum selection.");
        } else {
            sb.append("No stronger forum override was available, so the sealed report jurisdiction was retained.");
        }
        return sb.toString().trim();
    }

    private static String normalizeCode(String code) {
        String normalized = safe(code).toUpperCase(Locale.US);
        if ("SOUTH_AFRICA".equals(normalized) || "SA".equals(normalized)) {
            return "ZAF";
        }
        if ("UAE".equals(normalized) || "ZAF".equals(normalized) || "MULTI".equals(normalized)) {
            return normalized;
        }
        return "";
    }

    private static String displayNameFor(String code) {
        if ("UAE".equalsIgnoreCase(code)) {
            return "United Arab Emirates";
        }
        if ("ZAF".equalsIgnoreCase(code)) {
            return "South Africa";
        }
        return "Multi-jurisdiction";
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
