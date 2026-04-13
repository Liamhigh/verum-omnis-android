
package com.verum.omnis.ai;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * R&D Controller (stateless)
 * - Consumes RulesEngine diagnostics (no network, no persistence by default)
 * - Produces in-memory "training directives" to tighten thresholds for other brains
 * - Can be exported explicitly to a sealed PDF via forensic pipeline
 */
public class RnDController {

    public static class Feedback {
        public JSONObject report;     // human-readable JSON
        public double suggestedRiskWeightBoost; // meta-signal [0..0.5]
    }

    public static Feedback synthesize(Context ctx, RulesEngine.Result rules) {
        Feedback f = new Feedback();
        try {
            JSONObject j = new JSONObject();
            j.put("mode", "rules-only");
            j.put("keywords_hits", rules.diagnostics.optInt("keywords", 0));
            j.put("entities_hits", rules.diagnostics.optInt("entities", 0));
            j.put("evasion_hits", rules.diagnostics.optInt("evasion", 0));
            j.put("contradictions_hits", rules.diagnostics.optInt("contradictions", 0));
            j.put("concealment_hits", rules.diagnostics.optInt("concealment", 0));
            j.put("financial_hits", rules.diagnostics.optInt("financial", 0));
            j.put("top_liabilities", new JSONArray(rules.topLiabilities));
            j.put("risk_score", rules.riskScore);
            j.put("directive", deriveDirective(rules));
            f.report = j;
            f.suggestedRiskWeightBoost = clamp(0.0,
                    0.5,
                    0.02 * rules.diagnostics.optInt("contradictions", 0)
                    + 0.015 * rules.diagnostics.optInt("concealment", 0)
                    + 0.01  * rules.diagnostics.optInt("evasion", 0));
            return f;
        } catch (Exception e) {
            f.report = new JSONObject();
            f.suggestedRiskWeightBoost = 0.0;
            return f;
        }
    }

    private static JSONObject deriveDirective(RulesEngine.Result r) throws Exception {
        JSONObject d = new JSONObject();
        // Basic directives: which detectors to push harder
        d.put("prioritize_contradictions", r.diagnostics.optInt("contradictions", 0) >= 2);
        d.put("prioritize_concealment", r.diagnostics.optInt("concealment", 0) >= 1);
        d.put("tighten_evasion_threshold", r.diagnostics.optInt("evasion", 0) >= 2);
        d.put("reinforce_financial_flags", r.diagnostics.optInt("financial", 0) >= 2);
        d.put("min_keywords_entities", (r.diagnostics.optInt("keywords", 0) >= 3 && r.diagnostics.optInt("entities", 0) >= 1));
        return d;
    }

    private static double clamp(double lo, double hi, double v) {
        return Math.max(lo, Math.min(hi, v));
    }
}
