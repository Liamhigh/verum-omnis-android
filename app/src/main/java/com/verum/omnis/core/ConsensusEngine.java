package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ConsensusEngine {

    private ConsensusEngine() {}

    public static JSONObject build(AnalysisEngine.ForensicReport report, JSONObject legalAttorneyAnalysis) {
        JSONObject result = new JSONObject();
        JSONObject contradiction = report != null && report.tripleVerification != null
                ? report.tripleVerification.optJSONObject("antithesis")
                : null;
        int verified = contradiction != null ? contradiction.optInt("verifiedCount", 0) : 0;
        int candidate = contradiction != null ? contradiction.optInt("candidateCount", 0) : 0;

        putSafe(result, "ruleBasedPrimary", true);
        putSafe(result, "gemmaReviewerAvailable", true);
        putSafe(result, "phi3ReviewerInstalled", false);
        putSafe(result, "verifiedContradictions", verified);
        putSafe(result, "candidateContradictions", candidate);

        JSONArray verifiedBy = new JSONArray();
        verifiedBy.put("rules-engine");
        if (legalAttorneyAnalysis != null && legalAttorneyAnalysis.length() > 0) {
            verifiedBy.put("gemma-legal-layer");
        }
        putSafe(result, "verifiedBy", verifiedBy);

        String method = "rules-plus-gemma-legal-review-awaiting-second-local-model";
        String posture = verified > 0
                ? "verified-contradictions-present-without-full-second-model-consensus"
                : "provisional-until-second-local-model-is-installed";
        putSafe(result, "consensusMethod", method);
        putSafe(result, "posture", posture);
        putSafe(result, "note",
                "This block records which local reviewers were actually available. It must not be read as full triple-model consensus unless the second and third local model packs are installed and used.");
        return result;
    }

    private static void putSafe(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
