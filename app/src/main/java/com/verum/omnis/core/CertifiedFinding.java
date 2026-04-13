package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CertifiedFinding {
    public final JSONObject finding;
    public final FindingAudit audit;
    public final FindingCertification certification;

    public CertifiedFinding(JSONObject finding, FindingAudit audit, FindingCertification certification) {
        this.finding = finding;
        this.audit = audit;
        this.certification = certification;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject out = new JSONObject();
        String normalizedType = normalizeType(finding);
        out.put("type", normalizedType);
        out.put("rawType", rawType(finding));
        out.put("status", finding != null ? finding.optString("status", "") : "");
        out.put("actor", finding != null ? finding.optString("actor", "") : "");
        out.put("summary", finding != null ? bestText(finding.optString("summary", ""), finding.optString("whyItConflicts", ""), normalizedType) : normalizedType);
        out.put("excerpt", finding != null ? finding.optString("excerpt", "") : "");
        if (finding != null && finding.has("page")) {
            out.put("page", finding.optInt("page", 0));
        }
        out.put("anchors", finding != null && finding.optJSONArray("anchors") != null ? finding.optJSONArray("anchors") : new JSONArray());
        out.put("finding", finding);
        out.put("audit", audit.toJson());
        out.put("certification", certification.toJson());
        return out;
    }

    private static String normalizeType(JSONObject finding) {
        String raw = rawType(finding);
        if ("PROPOSITION_CONFLICT".equalsIgnoreCase(raw)
                || "NEGATION".equalsIgnoreCase(raw)
                || "NUMERIC".equalsIgnoreCase(raw)
                || "TIMELINE".equalsIgnoreCase(raw)
                || "LOCATION".equalsIgnoreCase(raw)) {
            return "CONTRADICTION";
        }
        return raw.isEmpty() ? "FINDING" : raw;
    }

    private static String rawType(JSONObject finding) {
        if (finding == null) {
            return "";
        }
        String findingType = finding.optString("findingType", "").trim();
        if (!findingType.isEmpty()) {
            return findingType;
        }
        return finding.optString("conflictType", "").trim();
    }

    private static String bestText(String primary, String fallback, String finalFallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
        }
        return finalFallback == null ? "" : finalFallback;
    }
}
